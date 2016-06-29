package org.telegram.mtproto.schedule;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 03.11.13
 * Time: 19:59
 */
public class PreparedPackage {
    private boolean isHighPriority;
    private int seqNo;
    private long messageId;
    private byte[] content;

    public PreparedPackage(int seqNo, long messageId, byte[] content, boolean isHighPriority) {
        this.seqNo = seqNo;
        this.messageId = messageId;
        this.content = content;
        this.isHighPriority = isHighPriority;
    }

    public boolean isHighPriority() {
        return isHighPriority;
    }

    public int getSeqNo() {
        return seqNo;
    }

    public long getMessageId() {
        return messageId;
    }

    public byte[] getContent() {
        return content;
    }
}
