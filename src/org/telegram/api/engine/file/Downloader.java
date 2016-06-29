package org.telegram.api.engine.file;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.telegram.api.TLAbsInputFileLocation;
import org.telegram.api.engine.Logger;
import org.telegram.api.engine.TelegramApi;
import org.telegram.api.upload.TLFile;

/**
 * Created by ex3ndr on 18.11.13.
 */
public class Downloader {
    public static final int FILE_QUEUED = 0;
    public static final int FILE_DOWNLOADING = 1;
    public static final int FILE_COMPLETED = 2;
    public static final int FILE_CANCELED = 3;
    public static final int FILE_FAILURE = 4;

    private final AtomicInteger fileIds = new AtomicInteger(1);

    private final String TAG;

    private TelegramApi api;

    private static final long DOWNLOAD_TIMEOUT = 30 * 1000;

    private static final long DEFAULT_DELAY = 15 * 1000;

    private static final int BLOCK_SIZE = 16 * 1024;

    private static final int PARALLEL_DOWNLOAD_COUNT = 2;

    private static final int PARALLEL_PARTS_COUNT = 4;

    private static final int BLOCK_QUEUED = 0;
    private static final int BLOCK_DOWNLOADING = 1;
    private static final int BLOCK_COMPLETED = 2;

    private ArrayList<DownloadTask> tasks = new ArrayList<DownloadTask>();

    private ArrayList<DownloadFileThread> threads = new ArrayList<DownloadFileThread>();

    private final Object threadLocker = new Object();

    private Random rnd = new Random();

    public Downloader(TelegramApi api) {
        this.TAG = api.toString() + "#Downloader";
        this.api = api;

        for (int i = 0; i < PARALLEL_PARTS_COUNT; i++) {
            DownloadFileThread thread = new DownloadFileThread();
            thread.start();
            threads.add(thread);
        }
    }

    public TelegramApi getApi() {
        return api;
    }

    private synchronized DownloadTask getTask(int taskId) {
        for (DownloadTask task : tasks) {
            if (task.taskId == taskId) {
                return task;
            }
        }
        return null;
    }

    public synchronized void cancelTask(int taskId) {
        DownloadTask task = getTask(taskId);
        if (task != null && task.state != FILE_COMPLETED) {
            task.state = FILE_CANCELED;
            Logger.d(TAG, "File #" + task.taskId + "| Canceled");
        }
        updateFileQueueStates();
    }

    public synchronized int getTaskState(int taskId) {
        DownloadTask task = getTask(taskId);
        if (task != null) {
            return task.state;
        }

        return FILE_CANCELED;
    }

    public void waitForTask(int taskId) {
        while (true) {
            int state = getTaskState(taskId);
            if ((state == FILE_COMPLETED) || (state == FILE_FAILURE) || (state == FILE_CANCELED)) {
                return;
            }
            synchronized (threadLocker) {
                try {
                    threadLocker.wait(DEFAULT_DELAY);
                } catch (InterruptedException e) {
                    Logger.e(TAG, e);
                    return;
                }
            }
        }
    }

    public synchronized int requestTask(int dcId, TLAbsInputFileLocation location, int size, String destFile, DownloadListener listener) {
        int blockSize = BLOCK_SIZE;
        int totalBlockCount = (int) Math.ceil(((double) size) / blockSize);

        DownloadTask task = new DownloadTask();
        task.listener = listener;
        task.blockSize = blockSize;
        task.destFile = destFile;
        try {
            task.file = new RandomAccessFile(destFile, "rw");
            task.file.setLength(size);
        } catch (FileNotFoundException e) {
            Logger.e(TAG, e);
        } catch (IOException e) {
            Logger.e(TAG, e);
        }
        task.taskId = fileIds.getAndIncrement();
        task.dcId = dcId;
        task.location = location;
        task.size = size;
        task.blocks = new DownloadBlock[totalBlockCount];
        for (int i = 0; i < totalBlockCount; i++) {
            task.blocks[i] = new DownloadBlock();
            task.blocks[i].task = task;
            task.blocks[i].index = i;
            task.blocks[i].state = BLOCK_QUEUED;
        }
        task.state = FILE_QUEUED;
        task.queueTime = System.nanoTime();
        tasks.add(task);

        Logger.d(TAG, "File #" + task.taskId + "| Requested");

        updateFileQueueStates();

        return task.taskId;
    }

    private synchronized DownloadTask[] getActiveTasks() {
        ArrayList<DownloadTask> res = new ArrayList<DownloadTask>();
        for (DownloadTask task : tasks) {
            if (task.state == FILE_DOWNLOADING) {
                res.add(task);
            }
        }
        return res.toArray(new DownloadTask[res.size()]);
    }

    private synchronized void updateFileQueueStates() {
        DownloadTask[] activeTasks = getActiveTasks();
        outer:
        for (DownloadTask task : activeTasks) {
            for (DownloadBlock block : task.blocks) {
                if (block.state != BLOCK_COMPLETED) {
                    continue outer;
                }
            }
            onTaskCompleted(task);
        }
        activeTasks = getActiveTasks();

        int count = activeTasks.length;
        while (count < PARALLEL_DOWNLOAD_COUNT) {
            long mintime = Long.MAX_VALUE;
            DownloadTask minTask = null;
            for (DownloadTask task : tasks) {
                if (task.state == FILE_QUEUED && task.queueTime < mintime) {
                    minTask = task;
                }
            }

            if (minTask == null) {
                break;
            }
            minTask.state = FILE_DOWNLOADING;
            Logger.d(TAG, "File #" + minTask.taskId + "| Downloading");
        }

        synchronized (threadLocker) {
            threadLocker.notifyAll();
        }
    }

    private synchronized void onTaskCompleted(DownloadTask task) {
        if (task.state != FILE_COMPLETED) {
            Logger.d(TAG, "File #" + task.taskId + "| Completed in " + (System.nanoTime() - task.queueTime) / (1000 * 1000L) + " ms");
            task.state = FILE_COMPLETED;
            try {
                if (task.file != null) {
                    task.file.close();
                    task.file = null;
                }
            } catch (IOException e) {
                Logger.e(TAG, e);
            }
        }
        updateFileQueueStates();
    }

    private synchronized void onTaskFailure(DownloadTask task) {
        if (task.state != FILE_FAILURE) {
            Logger.d(TAG, "File #" + task.taskId + "| Failure in " + (System.nanoTime() - task.queueTime) / (1000 * 1000L) + " ms");
            task.state = FILE_FAILURE;
            try {
                if (task.file != null) {
                    task.file.close();
                    task.file = null;
                }
            } catch (IOException e) {
                Logger.e(TAG, e);
            }
        }
        updateFileQueueStates();
    }

    private synchronized DownloadTask fetchTask() {
        DownloadTask[] activeTasks = getActiveTasks();
        if (activeTasks.length == 0) {
            return null;
        } else if (activeTasks.length == 1) {
            return activeTasks[0];
        } else {
            return activeTasks[rnd.nextInt(activeTasks.length)];
        }
    }

    private synchronized DownloadBlock fetchBlock() {
        DownloadTask task = fetchTask();
        if (task == null) {
            return null;
        }

        for (int i = 0; i < task.blocks.length; i++) {
            if (task.blocks[i].state == BLOCK_QUEUED) {
                task.blocks[i].state = BLOCK_DOWNLOADING;
                if (task.lastSuccessBlock == 0) {
                    task.lastSuccessBlock = System.nanoTime();
                }
                return task.blocks[i];
            }
        }

        return null;
    }

	private synchronized void onBlockDownloaded(DownloadBlock block, byte[] data) {
        try {
            if (block.task.file != null) {
                block.task.file.seek(block.index * block.task.blockSize);
				block.task.file.write(data);
            } else {
                return;
            }
        } catch (IOException e) {
            Logger.e(TAG, e);
        }
        block.task.lastSuccessBlock = System.nanoTime();
        block.state = BLOCK_COMPLETED;
        if (block.task.listener != null) {
            int downloadedCount = 0;
            for (DownloadBlock b : block.task.blocks) {
                if (b.state == BLOCK_COMPLETED) {
                    downloadedCount++;
                }
            }

            int percent = downloadedCount * 100 / block.task.blocks.length;
            block.task.listener.onPartDownloaded(percent, downloadedCount);
        }
        updateFileQueueStates();
    }

    private synchronized void onBlockFailure(DownloadBlock block) {
        block.state = BLOCK_QUEUED;
        if (block.task.lastSuccessBlock != 0 && (System.nanoTime() - block.task.lastSuccessBlock > DOWNLOAD_TIMEOUT * 1000L * 1000L)) {
            onTaskFailure(block.task);
        }
        updateFileQueueStates();
    }

    private class DownloadTask {

        public DownloadListener listener;
        public long lastNotifyTime;

        public int taskId;

        public int blockSize;

        public int dcId;
        public TLAbsInputFileLocation location;
        public int size;

        public long queueTime;

        public int state;

        public DownloadBlock[] blocks;

        public String destFile;

        public RandomAccessFile file;

        public long lastSuccessBlock;
    }

    private class DownloadBlock {
        public DownloadTask task;
        public int state;
        public int index;
    }

    private class DownloadFileThread extends Thread {

        public DownloadFileThread() {
            setName("DownloadFileThread#" + hashCode());
        }

        @Override
        public void run() {
            setPriority(Thread.MIN_PRIORITY);
            while (true) {
                Logger.d(TAG, "DownloadFileThread iteration");
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
                DownloadBlock block = fetchBlock();
                if (block == null) {
                    synchronized (threadLocker) {
                        try {
                            threadLocker.wait();
                            continue;
                        } catch (InterruptedException e) {
                            Logger.e(TAG, e);
                            return;
                        }
                    }
                }

                long start = System.nanoTime();
                Logger.d(TAG, "Block #" + block.index + " of #" + block.task.taskId + "| Starting");
                try {
                    TLFile file = api.doGetFile(block.task.dcId, block.task.location, block.index * block.task.blockSize, block.task.blockSize);
                    Logger.d(TAG, "Block #" + block.index + " of #" + block.task.taskId + "| Downloaded in " + (System.nanoTime() - start) / (1000 * 1000L) + " ms");
                    onBlockDownloaded(block, file.getBytes());
                } catch (IOException e) {
                    Logger.d(TAG, "Block #" + block.index + " of #" + block.task.taskId + "| Failure");
                    Logger.e(TAG, e);
                    onBlockFailure(block);
                }
            }
        }
    }
}
