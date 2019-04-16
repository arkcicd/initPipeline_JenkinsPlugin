#! /bin/bash

# thiss bash for now, translate into golang once this works...

# this takes urlPassed as an arg from initPipeline_JenkinsPlugin
if [[ -z $1 ]]; then
    echo "Usage: $0 urlPassed"
    echo "where urlPassed is the notifyCommit piece sent to Jenkins at /git"
    echo ""
    exit 1
fi
urlPassed="${1}"
echo "urlPassed:  ${urlPassed}"

# this comes through in idline in initpipeline plugin
echo "...and a hello world from your jenkins controller"
