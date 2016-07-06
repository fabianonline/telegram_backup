# Telegram_Backup
Copyright 2016 Fabian Schlenz  
Licensed under GPLv3

## Description
This is a small Java app that allows you to download all your history from
Telegram's servers and keep a local copy of them.

## Features
* You can use multiple accounts with this tool.
* Messages are saved in a SQLite database; media (documents, photos, videos,
  stickers, geolocations, audios) are downloaded and saved as files.
* A GUI is planned for later; at the moment this is a command line tool
  only.
* Incremental backups - if you run the tool at a later time, it will only
  download new messages / media.
* You will be able to use an HTML exporter to create static HTML files
  containing your chats. This feature is still in the works.

## Limitations
This tool relies on Telegram's API. Apparently they don't like people who
download lots of media files, so this app gets blocked after about 2000
downloaded media files. This is nothing bad; the app detects this and waits
the necessary amount of time as dictated by Telegram. Since this delay can
be quite large (I've seen 50 minutes), downloading lots and lots of media
can take some time.

But since this tool is designed to be able to continue it's work at any
time, you can just abort the download and continue it later - that way,
you'll be moving step by step towards a complete archive of your telegram
messages and media files.

## Usage
You need to have at least Java 1.7 installed on your machine. Download the
jar file, and run it on the console like this: `java -jar telegram_backup.jar`.

Append `--help` to get a list of all available commands.

Basically, you have to call it with `--login` first to login to your telegram account and then
call it again with `--account <phone>` to use this account and download all
it's history. If you have just one account, you can omit this parameter.

## Donations
I've put quite some time into this tool. If you want to donate a small
amount, you can send it via Bitcoin to *1CofYzS88iEngxMu4NqQeohWDBUHv9CNDJ* or via PayPal to
[mail@fabianonline.de](https://paypal.me/fabianonline).

Alternatively use [this link](http://www.amazon.com/?_encoding=UTF8&camp=1638&creative=6742&linkCode=ur2&site-redirect=de&tag=telegrambackup-21) the next time you shop at
amazon.com or [this
link](https://www.amazon.de/ref=as_li_ss_tl?ie=UTF8&linkCode=ll2&tag=telegrambackup-21&linkId=c54d9fe7c560128c6f018dd24e80d486) for amazon.de. You won't
pay any more, but I will get a few percent of your purchase's worth from
amazon.

## Attribution
This tool uses libraries from other developers which are covered by other licenses,
which are:
* [Kotlogram](https://github.com/badoualy/kotlogram) by Yannick Badoual, licensed under MIT License.
* [SQLite JDBC](https://bitbucket.org/xerial/sqlite-jdbc) by Taro L. Saito, licensed under Apache License version 2.0.
