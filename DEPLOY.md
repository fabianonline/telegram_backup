# Deploying a new version

* Update the version in the Dockerfile to the coming version.
* Commit the new Dockerfile.
* Merge into stable: `git checkout stable && git merge --no-ff master`
* Create a new tag for the new version: `git tag -a <version>`.
* Push everything to github: `git push --all && git push --tags`.
* Build it: `gradle build`.
* Create a new release on github for this version. 'Release title' has to be just the version string. Attach the newly built `telegram-backup.jar`.
* Build the docker image: `docker build -t fabianonline/telegram_backup:<version> -t fabianonline/telegram_backup:latest - < DOCKERFILE`.
* Push the docker image: `docker push fabianonline/telegram_backup`.
* Post a message into the telegram_backup users group.
