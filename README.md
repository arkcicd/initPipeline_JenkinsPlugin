# Init Pipeline Jenkins Plugin

## License

This software is licensed under the MIT open source license

see https://opensource.org/licenses/MIT

## Architecture

This plugin was derived from the Jenkins Git Plugin, specifically the GitStatus.java file.

This file was adapted to catch the case(s) where the notifyCommit url references a repo for which the Jenkins server 
does not have a pipeline job configured.

The Git Plugin drops the notifyCommit on the floor with a single log line to /var/log/jenkins/jenkins.log in this case.

We listen alongside and catch that case, calling an external golang binary to configure and build a new pipeline.

