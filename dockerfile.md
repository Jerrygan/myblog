FROM centos
MAINTAINER jerry<303834731@qq.com>

ENV MYPATH /usr/local
WORKDIR $MYPATH

RUN yum -y install vim
RUN yum -y install net-tools

EXPOSE 80

CMD echo $MYPATH
CMD echo "-----end----"
CMD /bin/bash



FROM centos

MAINTAINER jerry<303834731@qq.com>
#复制文件
COPY lib/README /usr/local/README
#复制解压
ADD lib/jdk1.8.zip /usr/local/
#复制解压
ADD lib/apache-tomcat-8.5.41-zjd.zip /usr/local/

RUN yum -y install vim
#设置环境变量
ENV MYPATH /usr/local
#设置工作目录
WORKDIR $MYPATH
#设置环境变量
ENV JAVA_HOME /usr/local/jdk1.8
#设置环境变量
ENV CATALINA_HOME /usr/local/apache-tomcat-8.5.41-zjd
#设置环境变量 分隔符是：
ENV PATH $PATH:$JAVA_HOME/bin:$CATALINA_HOME/lib
#设置暴露的端口
EXPOSE 8080
# 设置默认命令
CMD /usr/local/apache-tomcat-8.5.41-zjd/bin/startup.sh && tail -f /usr/local/apache-tomcat-8.5.41-zjd/logs/catalina.out

docker run -d -p 8080:8080 --name tomcat-zjd --privileged=true -v /home/tomcat/zjd:/usr/local/apache-tomcat-8.5.41-zjd/webapps/zjd -v /home/tomcat/tomcatlogs/:/usr/local/apache-tomcat-8.5.41-zjd/logs mytomcat:1.0


docker run -d --name nginx01 --privileged=true -p 8002:80 -v /home/nginx/conf:/etc/nginx/nginx.conf -v /home/nginx/www:/etc/nginx/html -v /home/nginx/logs:/var/log/nginx  nginx



docker run -d -p 10000:8080 --name tomcat-net-02 --net tomcatnet tomcat:8