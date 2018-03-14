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
release_notes=$(echo "$release_notes" | sed 's/\* /• /' | sed 's/&/&amp;/g' | sed 's/</\&lt;/g' | sed 's/>/\&gt;/g')
message="<b>New beta release $version</b>"$'\n\n'
message="${message}${additional_notes}"$'\n\n'
message="${message}Changes since the last <i>real</i> release:"$'\n'"${release_notes}"$'\n\n'
message="${message}<b>This is a release for testing purposes only. There may be bugs included that might destroy your data. Only use this beta release if you know what you're doing. AND MAKE A BACKUP OF YOUR BACKUP BEFORE USING IT!</b>"$'\n\n'
message="${message}Please report back if you used this release and encountered a bug. Also report back, if you used it and IT WORKED, please. Thank you."$'\n\n'
message="${message}https://files.fabianonline.de/${filename}"

result=$(curl https://api.telegram.org/bot${BOT_TOKEN}/sendMessage -XPOST --form "text=<-" --form-string "chat_id=${CHAT_ID}" --form-string "parse_mode=HTML" --form-string "disable_web_page_preview=true" <<< "$message")
message_id=$(jq -r '.result.message_id' <<< "$result")

echo "Pinning the new message..."
curl https://api.telegram.org/bot${BOT_TOKEN}/pinChatMessage -XPOST --form "chat_id=${CHAT_ID}" --form "message_id=${message_id}" --form "disable_notification=true"

echo "Done."
