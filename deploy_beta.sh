#!/bin/bash
error() {
	echo "Error: $1"
	exit 1
}

release_notes="$(cat release_notes.txt 2>/dev/null)"

source "deploy.secret.sh"
[ -z "$BOT_TOKEN" ] && error "BOT_TOKEN is not set or empty."
[ -z "$CHAT_ID" ] && error "CHAT_ID is not set or empty."

version=$(git describe --tags --dirty)

echo "Enter additional notes, end with Ctrl-D."
additional_notes="$(cat)"

echo "Building it..."
gradle build || error "Build failed. What did you do?!"

echo "Copying it to files.fabianonline.de..."
filename="telegram_backup.beta_${version}.jar"
cp --no-preserve "mode,ownership,timestamps" build/libs/telegram_backup.jar /data/containers/nginx/www/files/${filename}

echo "Notifying the Telegram group..."
release_notes=$(sed 's/\* /• /' | sed 's/&/&amp;/g' | sed 's/</\&lt;/g' | sed 's/>/\&gt;/g' <<< "$release_notes")
message="<b>New beta release $version</b>"$'\n'$'\n'"This is a beta release. There may be bugs included that might destroy your data. Only use this beta release if you know what you're doing. AND MAKE A BACKUP BEFORE USING IT!"$'\n'$'\n'"$additional_notes"$'\n'"$release_notes"$'\n'$'\n'"https://files.fabianonline.de/${filename}"

result=$(curl https://api.telegram.org/bot${BOT_TOKEN}/sendMessage -XPOST --form "text=<-" --form-string "chat_id=${CHAT_ID}" --form-string "parse_mode=HTML" --form-string "disable_web_page_preview=true" <<< "$message")
message_id=$(jq -r '.result.message_id' <<< "$result")

echo "Pinning the new message..."
curl https://api.telegram.org/bot${BOT_TOKEN}/pinChatMessage -XPOST --form "chat_id=${CHAT_ID}" --form "message_id=${message_id}" --form "disable_notification=true"

echo "Done."
