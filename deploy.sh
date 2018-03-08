#!/bin/bash
error() {
	echo "Error: $1"
	exit 1
}

[ -z "$1" ] && error "Parameter's missing. Expecting version number like '1.2.3' as first and only parameter."

if [ "$1" == "--help" ]; then
	echo "Usage: `basename "$0"` 1.2.3"
	exit 1
fi

release_notes="$(cat release_notes.txt 2>/dev/null)"
[ -z "$release_notes" ] && error "release_notes.txt is empty"

VERSION="$1"

source "deploy.secret.sh"
[ -z "$BOT_TOKEN" ] && error "BOT_TOKEN is not set or empty."
[ -z "$CHAT_ID" ] && error "CHAT_ID is not set or empty."
[ -z "$TOKEN" ] && error "TOKEN is not set or empty."

CURL_OPTS="-u fabianonline:$TOKEN"

git diff-files --quiet --ignore-submodules -- || error "You have changes in your working tree."

git diff-index --cached --quiet HEAD --ignore-submodules -- || error "You have uncommited changes."

branch_name=$(git symbolic-ref HEAD 2>/dev/null)
branch_name=${branch_name##refs/heads/}
[ "$branch_name" == "master" ] || error "Current branch is $branch_name, not master."

echo "Updating the Dockerfile..."
sed -i "s/ENV JAR_VERSION .\+/ENV JAR_VERSION $VERSION/g" Dockerfile || error "Couldn't modify Dockerfile."

echo "Committing the new Dockerfile..."
git commit -m "Bumping the version to $VERSION" Dockerfile

echo "Tagging the new version..."
git tag -a "$VERSION" -m "Version $VERSION" || error

echo "Building it..."
gradle build || error "Build failed. What did you do?!"

echo "Checking out stable..."
git checkout stable || error

echo "Merging master into stable..."
git merge --no-ff -m "Merging master into stable for version $VERSION" master || error

echo "Pushing all to Github..."
git push --all || error

echo "Pushing tags to Github..."
git push --tags || error

echo "Generating a release on Github..."
json=$(ruby -e "require 'json'; puts({tag_name: '$VERSION', name: '$VERSION', body: \$stdin.read}.to_json)" <<< "$release_notes") || error "Couldn't generate JSON for Github"

json=$(curl $CURL_OPTS https://api.github.com/repos/fabianonline/telegram_backup/releases -XPOST -d "$json") || error "Github failure"

echo "Uploading telegram_backup.jar to Github..."
upload_url=$(jq -r ".upload_url" <<< "$json") || error "Could not parse JSON from Github"
upload_url=$(sed 's/{.*}//' <<< "$upload_url")
release_url=$(jq -r ".html_url" <<< "$json") || error "Could not parse JSON from Github"
curl $CURL_OPTS --header "Content-Type: application/zip" "${upload_url}?name=telegram_backup.jar" --upload-file build/libs/telegram_backup.jar || error "Asset upload to github failed"

echo "Building the docker image..."
docker build -t fabianonline/telegram_backup:$VERSION -t fabianonline/telegram_backup:latest - < Dockerfile

echo "Pushing the docker image..."
docker push fabianonline/telegram_backup

echo "Notifying the Telegram group..."
release_notes=$(sed 's/\* /• /' <<< "$release_notes")
message="Version $VERSION released"$'\n'$'\n'"$release_notes"$'\n'$'\n'"$release_url"

curl https://api.telegram.org/bot${BOT_TOKEN}/sendMessage -XPOST --form "text=<-" --form-string "chat_id=${CHAT_ID}" <<< "$message"

echo "Cleaning release_notes.txt..."
> release_notes.txt

echo "Checking out master..."
git checkout master

echo "Done."
