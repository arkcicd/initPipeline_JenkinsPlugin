#! /bin/bash

# this is bash for now, translate into golang once this works...

# this takes urlPassed as an arg from initPipeline_JenkinsPlugin
if [[ -z $1 ]]; then
    echo "Usage: $0 NOTIFYURL"
    echo "where NOTIFYURL is the notifyCommit piece sent to Jenkins at /git"
    echo ""
    exit 1
fi
NOTIFYURL="${1}"
echo "NOTIFYURL:  ${NOTIFYURL}"

# this comes through in idline in initpipeline plugin
echo "...and a hello world from your jenkins controller"

## the original java code sequence was...



### getEnvVars() 
#   retrieved env vars from /etc/sysconfig/jenkins

# quick and dirty, get the values
source /etc/sysconfig/jenkins



### checkDebug() to set or unset debug flag through code, this stayed in plugin java



### getProjectAndRepo() 
#   isolate from NOTIFYURL the project and repo

# NOTIFYURL looks something like 
# git@bitbucket.org:xosdigital/initpipeline_jenkinsplugin.git
# OR
# ssh://git@bitbucket.org/xosdigital/initpipeline_jenkinsplugin.git
# OR 
# https://bitbucket.org/xosdigital/initpipeline_jenkinsplugin.git 

# set PROJECT, REPO
if [[ ${NOTIFYURL} =~ 'ssh://' ]]; then
    echo "found ssh:// construct..."
    # ssh://git@bitbucket.org/xosdigital/initpipeline_jenkinsplugin.git
    # separate on ":", get ssh + everything else
    # then drop that result through "/" and print... $2 for PROJECT, $3 for REPO 
    PROJECT=`echo ${NOTIFYURL} | awk -F: '{ print $2 }' | awk -F'/' '{ print $2 }'`
    REPOFULL=`echo $NOTIFYURL | awk -F: '{ print $2 }' | awk -F'/' '{ print $3 }'` 
    CONFIGURL="${NOTIFYURL}"  # set configurl to the string passed...
elif [[ ${NOTIFYURL} =~ 'http://' ]]; then
    echo "found http(s) construct..."
    # https://bitbucket.org/xosdigital/initpipeline_jenkinsplugin.git
    # separate on :, get http)s) + everything else...
    # separate again on "/", get ???
    PROJECT=`echo $NOTIFYURL | awk -F: '{ print $2 }' | awk -F'/' '{ print $4 }'`
    REPOFULL=`echo $NOTIFYURL | awk -F: '{ print $2 }' | awk -F'/' '{ print $5 }'`
    CONFIGURL="${NOTIFYURL}"
else
    echo "found clone-style repo url..."
    SERVER=`echo ${NOTIFYURL} | awk -F: '{ print $1 }'` # catch the server string piece here
    PROJECT=`echo $NOTIFYURL | awk -F: '{ print $2 }' | awk -F'/' '{ print $1 }'`
    REPOFULL=`echo $NOTIFYURL | awk -F: '{ print $2 }' | awk -F'/' '{ print $2 }'`
    # a bit more work could be done here...
    # the git@bitbucket.org:project/repo construct doesn't work in the config.xml, so must be translated
    # replace the ":"
    CONFIGURL="ssh://${SERVER}/${PROJECT}/${REPOFULL}"
    # should get us "ssh://" + "git@bitbucket.org" + / + "xosdigital" + / + "initpipeline_jenkinsplugin.git"
fi
# repo still has the .git extension, maybe...
if [[ ${REPOFULL} =~ .*\.git$ ]]; then
    REPO=`echo ${REPOFULL} | sed 's/\.[^.]*$//'`
else
    REPO="${REPOFULL}"
fi

# check
echo "**SERVER (if set): ${SERVER}"
echo "**REPO: ${REPO}"
echo "**PROJECT: ${PROJECT}"
echo "**CONFIGURL:  ${CONFIGURL}"


### checkCicdRepo() - this was a check for GHE MultiBranch Org setup
#   if the ghe-config (or something like that) repo existed, this was a multibranch construct and got treated
#   differently



### latestCommitHash()
#   because of the unreliability of the git plugin in determining latest commit
#   without extensive build history, this isolated the latest commithash to the repo passed

# may need more differentiation if someone actually sends an http(s) url.  Auth to bitbucket is is via ssh id_rsa
# http access or https access to public repos will work, but restricted probably not so much
# for now assume this auth works

# set up a unique working dir
DATE=`date +%Y%m%d%H%M%S`
WORKSDIR="/tmp/initpipeline_space/${DATE}"
mkdir -p ${WORKSDIR}

# create an empty repo...
echo "initiating local repo at ${WORKSDIR}/workspace"
git init ${WORKSDIR}/workspace

# cd to it
echo "change to the repo directory..."
cd ${WORKSDIR}/workspace
echo "check current directory..."
pwd

# fetch branches and tags
echo "do the fetch..."
echo "notifyurl:  ${NOTIFYURL}"
git fetch --tags --progress ${NOTIFYURL} +refs/heads/*:refs/remotes/origin/*
echo "fetch completed, config remote..."
git config remote.origin.url ${NOTIFYURL}
echo "let's see what we have..."
git remote -v 
echo "go find the latest commit..."
GITHASH=`git log --all -n 1 | egrep ^commit | awk '{ print $2 }'`
echo "${GITHASH}"  # return GITHASH

# $GITHASH has the last commit hash



### retestForJob () 
#   sometimes the git plugin would pass a url through, but miss that the job for our purposes already 
#   exists, so this dd a direct simple check to see if the directory for the job exists
#   called doCreateJob() if the job is truly found not to exist, and that continues execution

# pipeline job naming is repo_project...
# we can get the jenkins_home from /etc/ansible/devops/jenkins_vars.yml if it exists
if [[ -f '/etc/ansible/devops/jenkins_vars.yml' ]]; then
    # grab jenkins_home string, this has leading and trailing quotes...
    RAW_JENKINS_HOME=`grep jenkins_home /etc/ansible/devops/jenkins_vars.yml | awk '{ print $2 }'`
    # remove the leading and trailing quotes...
    JENKINS_HOME=`sed -e 's/^"//' -e 's/"$//' <<<"$RAW_JENKINS_HOME"`
    echo "jenkin's home:  $JENKINS_HOME"
else
    # if no file, assume jenkins_home = /var/lib/jenkins
    echo "/etc/ansible/devops/jenkins_vars.yml not found..."
    echo "setting JENKINS_HOME to /evar/lib/jenkins..."
    JENKINS_HOME='/var/lib/jenkins'
fi

# go look for the directory
TARGET="${JENKINS_HOME}/jobs/${REPO}_${PROJECT}"
echo "checking target $TARGET..."
if [[ -d "${TARGET}" ]]; then
    echo "ERROR:: found this job already exists, exiting"
    exit 1
fi



### doCreateJob() 
#   calls editPipelineConfig(), callAndCreatePipeline(), which cascades execution through
#   not needed here... 


### editPipelineConfig() 
#   takes the template pipeline config.xml and edits it, crafting and placing the 
#   <url> and <projectUrl> tages to the correct repo url from urlPassed

# template is at $JENKINS_HOME/templates/config.xml.pipeline
# copy this into $WORKSDIR
echo "copying template over..."
cp ${JENKINS_HOME}/templates/config.xml.pipeline ${WORKSDIR}/config.xml
echo "check config.xml..."
ls -la ${WORKSDIR}
# replace <url> and <projectUrl> strings
 
# sed and xmllint were way over complicated as well as not working so...
TMPCONF="${WORKSDIR}/tmp_config.xml"
rm -f $TMPCONF # remove if exists

# add newline at end of config.xml
# text processing demands the last line end in newline, and for some reason 
# without this added newline, the last line of the file gets dropped...
echo "" >> ${WORKSDIR}/config.xml

while IFS= read -r line; do
    if [[ $line =~ '<url>' ]]; then 
        echo "found url..."
        echo "          <url>${CONFIGURL}</url>" >> $TMPCONF
    elif [[ $line =~ '<projectUrl>' ]]; then
        echo "found projectUrl..."
        echo "      <projectUrl>${CONFIGURL}</projectUrl>" >> $TMPCONF
    else
        echo "$line" >> $TMPCONF
    fi     
done < ${WORKSDIR}/config.xml



### callAndCreatePipeline() 
#   calls procureJenkinsCrumb() and pullUserApi(), which gather credentials needed
#   and then makes a POST call to "createItem?name=" passing the edited config.xml for a pipeline job and creating the job
#   then calls restrictConfigXml() which edits the config to restrict it to building the latest commithash

# get the jenkins crumb...
# CRUMB=$(curl -s "http://devops:${API}@localhost:8080/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)")
# /home/centos/.ssh/devopsUserApi has the crumb result string
# [root@ip-10-200-35-46 .ssh]# echo $CRUMB
# Jenkins-Crumb:c06606ce60a0fb746825e328f15f7d41
# I set $API by catting the devopsUserApi file, so this should work for this...

API=`cat /home/centos/.ssh/devopsUserApi`
CRUMB=$(curl -s "http://devops:${API}@localhost:8080/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)")
echo $CRUMB

# check the config.xml
echo ""
echo "config.xml"
echo ""
cat ${WORKSDIR}/tmp_config.xml
echo ""
echo "end config.xml"
echo ""

# curl -s -X POST "http://devops:${API}@localhost:8080/createItem?name=${REPO}_${PROJECT}" --data-binary @${WORKSDIR}/config.xml -H "${CRUMB}" -H "Content-Type:text/xml"
# curl -s -X POST "http://devops:${API}@localhost:8080/createItem?name=${REPO}_${PROJECT}"  --data-binary @${WORKSDIR}/tmp_config.xml -H "${CRUMB}" -H "Content-Type:text/xml"

RESPONSE=`curl -s -X POST "http://devops:${API}@localhost:8080/createItem?name=${REPO}_${PROJECT}" --write-out "%{http_code}\n"  --data-binary @${TMPCONF} -H "${CRUMB}" -H "Content-Type:text/xml"`
if [[ $RESPONSE == 200 ]]; then
    echo "CREATE pipeline job ${REPO}_${PROJECT} succeeded..."
else
    echo "FAILED:: CREATE: createItem failed for ${REPO}_${PROJECT} - Exiting..."
    exit 1
fi



### procureJenkinsCrumb() 
#   gets the crumb string for use in calling the createItem and build endpoints in jenkins
#   API=`cat /home/centos/.ssh/devopsUserApi`
#   CRUMB=$(curl -s "http://devops:${API}@localhost:8080/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)")
#   echo $CRUMB
#   as above...



### pullUserApi() 
#   grabs the devopsUserApi file content and presents that to jenkins
#   this MAY have changed in the same way the reload_config.sh script had to adjust as jenkins made under-the-hood
#   security model changes, so this may also need to adjust to that
#   implemented above...


### restrictConfigXml() 
#   edits the config.xml for the new pipeline job, injecting the latest commithash as the target to build, 
#   then calls buildPipeline()

# so here we have the job created, but it would build incorrectly - jenkins initial build call would look
# for history and then give up and build the next alphabetical branch...
# turns out the "Branch Specifier" field can take a commit hash...
# start with tmp_config.xml ($TMPCONF)
#          <name>**</name>

# <name> will be toggled between these two values
WIDEOPEN='          <name>**</name>'
RESTRICTED="          <name>${GITHASH}</name>"

# next tmp file
TMPTOO="${WORKSDIR}/tmptoo_config.xml"

# add the newline at the end of the file
echo "" >> $TMPCONF
# and read through, changing the Branch Specifier xml tag...
while IFS= read -r line; do
    if [[ $line =~ '<name>' ]]; then
        echo "found Branch Specifier..."
        echo "${RESTRICTED}" >> $TMPTOO
    else
        echo "$line" >> $TMPTOO
    fi
done < $TMPCONF

# POST this as config.xml update...
RESPONSE=`curl -s -X POST "http://devops:${API}@localhost:8080/job/${REPO}_${PROJECT}/config.xml" --write-out "%{http_code}\n"  --data-binary @${TMPTOO} -H "${CRUMB}" -H "Content-Type:text/xml"`
if [[ $RESPONSE == 200 ]]; then
    echo "RESTRICT pipeline job ${REPO}_${PROJECT} succeeded..."
else
    echo "FAILED:: RESTRICT failed for ${REPO}_${PROJECT} - Exiting..."
    exit 1
fi



### buildPipeline() 
#   sends /build to the pipeline - once the job registers as building, then calls removeRestrictedConfigXml()

ESPONSE=`curl -s -X POST "http://devops:${API}@localhost:8080/job/${REPO}_${PROJECT}/build" --write-out "%{http_code}\n" -H "${CRUMB}" -H "Content-Type:text/xml"`
if [[ $RESPONSE == 200 ]]; then
    echo "BUILD pipeline job ${REPO}_${PROJECT} succeeded..."
else
    echo "FAILED:: BUILD failed for ${REPO}_${PROJECT} - Exiting..."
    exit 1
fi




### removeRestrictedConfigXml() 
#   sleeps 10 seconds, then loops through until it sees the commit hash in the response from the jenkins server, 
#   once it sees the build present and running, edit the commithash out of the config.xml and return to "**"

# the first step is to ensure this is actually building BEFORE frestting the restriction...
# loop through checking build status
CHECKBUILD="Null"
while [[ ${CHECKBUILD} == "Null" ]]; do
    ESPONSE=`curl -s -X POST "http://devops:${API}@localhost:8080/job/${REPO}_${PROJECT}/1/api/xml" -H "${CRUMB}" -H "Content-Type:text/xml"`
    echo "${RESPONSE}"
    sleep 5
done

### complete
