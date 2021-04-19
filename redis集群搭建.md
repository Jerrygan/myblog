准备镜像

docker images

启动容器

docker run -p 6371:6379 -p 16371:16379 --name redis-1 \

> -v /home/redis/node-1/data:/data \ -v /home/redis/node-1/conf/redis.conf:/etc/redis/redis.conf \ -d --net redis --ip 192.169.0.11 redis redis-server /etc/redis/redis.conf





创建redis集群网络

```
[root@localhost home]# docker network create redis --subnet 192.169.0.0/16 --gateway 192.169.0.1

beb8f62e92623408a6927e4e190588b3b4487c56c953c193b06ac04cc74c43b8
[root@localhost home]# docker network inspect redis
[
    {
        "Name": "redis",
        "Id": "beb8f62e92623408a6927e4e190588b3b4487c56c953c193b06ac04cc74c43b8",
        "Created": "2021-03-19T14:52:56.611335661+08:00",
        "Scope": "local",
        "Driver": "bridge",
        "EnableIPv6": false,
        "IPAM": {
            "Driver": "default",
            "Options": {},
            "Config": [
                {
                    "Subnet": "192.169.0.0/16",
                    "Gateway": "192.169.0.1"
                }
            ]
        },
        "Internal": false,
        "Attachable": false,
        "Containers": {},
        "Options": {},
        "Labels": {}
    }
]
```

启动redis-node-1节点

```
[root@localhost home]# docker run -p 6371:6379 -p 16371:16379 --name redis-1 \
> -v /home/redis/node-1/data:/data \
> -v /home/redis/node-1/conf/redis.conf:/etc/redis/redis.conf \
> -d --privileged=true --net redis --ip 192.169.0.11 redis redis-server /etc/redis/redis.conf
e5bf131f04c2767c5c4cc57d4003e42df9832cdfb11d2039f80b12816230b723
[root@localhost home]# docker ps -a
CONTAINER ID        IMAGE                 COMMAND                  CREATED             STATUS              PORTS                                                                                                         NAMES
e5bf131f04c2        redis                 "docker-entrypoint..."   4 seconds ago       Up 3 seconds        0.0.0.0:6371->6379/tcp, 0.0.0.0:16371->16379/tcp                                                              redis-1
d2bf8d48879e        rabbitmq:management   "docker-entrypoint..."   2 months ago        Up 6 weeks          4369/tcp, 5671/tcp, 0.0.0.0:5672->5672/tcp, 15671/tcp, 15691-15692/tcp, 25672/tcp, 0.0.0.0:15672->15672/tcp   rabbitMq
[root@localhost home]# 

```

然后查看redis网络

```
[root@localhost home]# docker network inspect redis
[
    {
        "Name": "redis",
        "Id": "beb8f62e92623408a6927e4e190588b3b4487c56c953c193b06ac04cc74c43b8",
        "Created": "2021-03-19T14:52:56.611335661+08:00",
        "Scope": "local",
        "Driver": "bridge",
        "EnableIPv6": false,
        "IPAM": {
            "Driver": "default",
            "Options": {},
            "Config": [
                {
                    "Subnet": "192.169.0.0/16",
                    "Gateway": "192.169.0.1"
                }
            ]
        },
        "Internal": false,
        "Attachable": false,
        "Containers": {
            "e5bf131f04c2767c5c4cc57d4003e42df9832cdfb11d2039f80b12816230b723": {
                "Name": "redis-1",
                "EndpointID": "886ade19abc0234b93c3f03c0c8c847e6216be7f07f0ddc85ac971eb290976d5",
                "MacAddress": "02:42:c0:a9:00:0b",
                "IPv4Address": "192.169.0.11/16",
                "IPv6Address": ""
            }
        },
        "Options": {},
        "Labels": {}
    }
]

```