REM Windows batch file to 
REM build the protobuf classes from the data.proto. Note tested with 
REM protobuf 2.4.1. Current version is 2.5.0.
REM
REM Building: 
REM Running this batch file is only needed when the protobuf structures 
REM have changed.

project_base="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# which protoc that you built
#PROTOC_HOME=/usr/local/protobuf-2.4.1/
PROTOC_HOME=/usr/local/protobuf-2.5.0/

if [ -d ${project_base}/generated ]; then
  rm -r ${project_base}/generated/*
fi


$PROTOC_HOME/bin/protoc --proto_path=${project_base}/resources --java_out=${project_base}/generated ${project_base}/resources/app.proto

$PROTOC_HOME/bin/protoc --proto_path=${project_base}/resources --java_out=${project_base}/generated ${project_base}/resources/mgmt.proto
