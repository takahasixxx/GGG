#################################
# (C) Copyright IBM Corp. 2018
#################################
FROM centos:7

ADD /docker/BBMServer /BBMServer

RUN yum update -y

RUN yum install -y git

RUN yum install -y java

RUN yum install -y https://centos7.iuscommunity.org/ius-release.rpm
RUN yum install -y python36u python36u-libs python36u-devel python36u-pip
RUN pip3.6 install --upgrade pip
RUN pip3.6 install -r /BBMServer/requirements.txt

EXPOSE 10080

RUN chmod +x BBMServer/run.sh
CMD ["./BBMServer/run.sh"]