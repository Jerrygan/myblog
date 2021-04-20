1.创建redis集群网络

![微信截图_20210414172514](https://i.loli.net/2021/04/14/gbR5OCIZhfLH8lK.png)

```bash
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



2.创建redis配置文件

```
# 搭建3主3从 共6节点
for port in $(seq 7002 7007);do

mkdir -p node-${port}/{conf,data}
touch node-${port}/conf/redis.conf
cat << EOF >node-${port}/conf/redis.conf
##节点端口
port 6379       
##允许任何来源
bind 0.0.0.0          
## 是为了禁止公网访问redis cache，加强redis安全的。它启用的条件，有两个：1） 没有bind IP 2） 没有设置访问密码 启用后只能够通过lookback ip（127.0.0.1）访问Redis cache，如果从外网访问，则会返回相应的错误信息 
protected-mode no
##cluster集群模式
cluster-enabled yes
##用来保存集群状态信息，可以自定义配置名。
cluster-config-file nodes.conf   
## 如果要最大的可用性，值设置为0。定义slave和master失联时长的倍数，如果值为0，则只要失联slave总是尝试failover，而不管与master失联多久。-----如果不加该参数，集群中的节点宕机后不会进行高可用恢复！！！！！
cluster-replica-validity-factor 0
# 定义slave多久（秒）ping一次master，如果超过repl-timeout指定的时长都没有收到响应，则认为master挂了
repl-ping-replica-period 1
##超时时间
cluster-node-timeout 5000   
##节点映射端口
cluster-announce-port 6379        
##节点总线端口
cluster-announce-bus-port 16379    
##实际为各节点网卡分配ip
#cluster-announce-ip 192.168.XX.XX 
##redis密码
requirepass  123456         
##表示m秒内数据集存在n次修改时，自动触发bgsave
## 手动执行save该命令会阻塞当前Redis服务器，执行save命令期间，Redis不能处理其他命令，直到RDB过程完成为止。
## 显然该命令对于内存比较大的实例会造成长时间阻塞，这是致命的缺陷，为了解决此问题，Redis提供了第二种方式----bgsave
## 执行bgsave命令时，Redis会在后台异步进行快照操作，快照同时还可以响应客户端请求。具体操作是Redis进程执行fork操作创建子进程，RDB持久化过程由子进程负责，完成后自动结束。阻塞只发生在fork阶段，一般时间很短。
## 基本上 Redis 内部所有的RDB操作都是采用 bgsave 命令。
## 执行执行 flushall 命令，也会产生dump.rdb文件，但里面是空的.
#关闭RDB功能的话,配置这个即可以，其他save要注释掉
#save 300 10 
save ""
##它是数据文件。当采用快照模式备份（持久化）时，Redis 将使用它保存数据，将来可以使用它恢复数据。
#dbfilename dump.rdb    
##持久化模式
appendonly yes
#appendfilename appendonly.aof
aof-use-rdb-preamble yes
# 文件达到64m时进行重写，然后如果文件大小增长了一倍，也会触发重写。
auto-aof-rewrite-min-size 64mb
auto-aof-rewrite-percentage 100
##AOF 文件和 Redis 命令是同步频率的，假设配置为 always，其含义为当 Redis 执行命令的时候，则同时同步到 AOF 文件，这样会使得 Redis 同步刷新 AOF 文件，造成缓慢。而采用 evarysec 则代表
## 每秒同步一次命令到 AOF 文件。
appendfsync everysec            
pidfile redis.pid
# 后台运行 ---- docker中使用后台运行将无法启动容器(应该是容器无法检测后台运行进程)
# daemonize yes
EOF	
# 修改第一个匹配到的port 6379 为 port $port
sed -i "0,/port 6379/s//port $port/" node-${port}/conf/redis.conf
sed -i "0,/announce-port 6379/s//announce-port $port/" node-${port}/conf/redis.conf
sed -i "0,/bus-port 16379/s//bus-port 1$port/" node-${port}/conf/redis.conf
done

```

运行节点容器

```bash
    # 运行节点容器
    for port in `seq 7002 7007`; do 
    docker run -it -p ${port}:${port} \
    --restart always \
    --name=redis-${port} \
    --net redis  \
    --privileged=true  \
    -v `pwd`/node-${port}/conf/redis.conf:/usr/local/etc/redis/redis.conf \
    -v  `pwd`/node-${port}/data:/data  \
    -d redis:latest redis-server /usr/local/etc/redis/redis.conf; 
     done
```

```bash
# 查看redis运行情况
docker ps -a |grep redis
```

查看所有容器节点

```bash
[root@localhost redis]# for port in `seq 7002 7007`; do 
> docker run -it -p ${port}:${port} \
> --restart always \
> --name=redis-${port} \
> --net redis  \
> --privileged=true  \
> -v `pwd`/node-${port}/conf/redis.conf:/usr/local/etc/redis/redis.conf \
> -v  `pwd`/node-${port}/data:/data  \
> -d redis:latest redis-server /usr/local/etc/redis/redis.conf; 
>  done
e6d68d9d981ce4403bdf528bdda12568cd0e61d1dd9ae12f7392ca0be9d73857
79029f8179d57a0d4de6306bc60be679211948d1d16a2c242fe4b82914f8412a
0ba0be6e56d0f81f731c4266de76dd7beefe57e7fb708c700d5f6e11e0b5c154
5a4036b86f5ef52a2184b1cff18268331a523a8c8d4eae43bead4ecdf1d0d866
0f547721f80bfb2d666ed1c0133aba43982ca0a529322f11d554e3b946b16a69
c5de5c8e39a8774b403da7cdb64b6f29c3d5860d5a91d927fbacb694d1c0b143
[root@localhost redis]# docker ps -a
CONTAINER ID        IMAGE                 COMMAND                  CREATED             STATUS              PORTS                                                                                                         NAMES
c5de5c8e39a8        redis:latest          "docker-entrypoint..."   6 seconds ago       Up 4 seconds        6379/tcp, 0.0.0.0:7007->7007/tcp                                                                              redis-7007
0f547721f80b        redis:latest          "docker-entrypoint..."   7 seconds ago       Up 5 seconds        6379/tcp, 0.0.0.0:7006->7006/tcp                                                                              redis-7006
5a4036b86f5e        redis:latest          "docker-entrypoint..."   7 seconds ago       Up 6 seconds        6379/tcp, 0.0.0.0:7005->7005/tcp                                                                              redis-7005
0ba0be6e56d0        redis:latest          "docker-entrypoint..."   8 seconds ago       Up 6 seconds        6379/tcp, 0.0.0.0:7004->7004/tcp                                                                              redis-7004
79029f8179d5        redis:latest          "docker-entrypoint..."   8 seconds ago       Up 7 seconds        6379/tcp, 0.0.0.0:7003->7003/tcp                                                                              redis-7003
e6d68d9d981c        redis:latest          "docker-entrypoint..."   8 seconds ago       Up 7 seconds        6379/tcp, 0.0.0.0:7002->7002/tcp                                                                              redis-7002
d2bf8d48879e        rabbitmq:management   "docker-entrypoint..."   2 months ago        Up 6 weeks          4369/tcp, 5671/tcp, 0.0.0.0:5672->5672/tcp, 15671/tcp, 15691-15692/tcp, 25672/tcp, 0.0.0.0:15672->15672/tcp   rabbitMq
[root@localhost redis]# 

```

查看各个redisIP

```bash
docker network inspect  redis-net  | grep -E "IPv4Address|Name"
```

进入redis-7007节点创建集群

```bash
# 进入docker redis-7007
docker exec -it  $(docker ps -qn 1) bash
docker exec -it  $(docker ps -qn 2) bash
# 创建集群
redis-cli -p 7007 --cluster create 192.169.0.2:7002 192.169.0.3:7003 192.169.0.4:7004 192.169.0.5:7005 192.169.0.6:7006 192.169.0.7:7007 -a 123456 --cluster-replicas 1

```

查看返回结果 输入yes

```bash
root@c5de5c8e39a8:/data# redis-cli -p 7007 --cluster create 192.169.0.2:7002 192.169.0.3:7003 192.169.0.4:7004 192.169.0.5:7005 192.169.0.6:7006 192.169.0.7:7007 -a 123456 --cluster-replicas 1
Warning: Using a password with '-a' or '-u' option on the command line interface may not be safe.
>>> Performing hash slots allocation on 6 nodes...
Master[0] -> Slots 0 - 5460
Master[1] -> Slots 5461 - 10922
Master[2] -> Slots 10923 - 16383
Adding replica 192.169.0.6:7006 to 192.169.0.2:7002
Adding replica 192.169.0.7:7007 to 192.169.0.3:7003
Adding replica 192.169.0.5:7005 to 192.169.0.4:7004
M: 70f9c714d051b11c3334ee1fe4c92712d7f8b485 192.169.0.2:7002
   slots:[0-5460] (5461 slots) master
M: bec09129ce7f7df0937b711bf42c22c8740ad868 192.169.0.3:7003
   slots:[5461-10922] (5462 slots) master
M: 71012eb26db37f5417182131d58e3905ba042fd7 192.169.0.4:7004
   slots:[10923-16383] (5461 slots) master
S: 2f172f56723c164a0ed9c6fdae575d3469e2c8b0 192.169.0.5:7005
   replicates 71012eb26db37f5417182131d58e3905ba042fd7
S: 6dd0ea5d8cfa10e89a052fbc90c864b63b10ac92 192.169.0.6:7006
   replicates 70f9c714d051b11c3334ee1fe4c92712d7f8b485
S: 911f7ce08e901b298fd5661335b81499836b2f35 192.169.0.7:7007
   replicates bec09129ce7f7df0937b711bf42c22c8740ad868
Can I set the above configuration? (type 'yes' to accept): yes
>>> Nodes configuration updated
>>> Assign a different config epoch to each node
>>> Sending CLUSTER MEET messages to join the cluster
Waiting for the cluster to join
.
>>> Performing Cluster Check (using node 192.169.0.2:7002)
M: 70f9c714d051b11c3334ee1fe4c92712d7f8b485 192.169.0.2:7002
   slots:[0-5460] (5461 slots) master
   1 additional replica(s)
M: bec09129ce7f7df0937b711bf42c22c8740ad868 192.169.0.3:7003
   slots:[5461-10922] (5462 slots) master
   1 additional replica(s)
S: 911f7ce08e901b298fd5661335b81499836b2f35 192.169.0.7:7007
   slots: (0 slots) slave
   replicates bec09129ce7f7df0937b711bf42c22c8740ad868
M: 71012eb26db37f5417182131d58e3905ba042fd7 192.169.0.4:7004
   slots:[10923-16383] (5461 slots) master
   1 additional replica(s)
S: 2f172f56723c164a0ed9c6fdae575d3469e2c8b0 192.169.0.5:7005
   slots: (0 slots) slave
   replicates 71012eb26db37f5417182131d58e3905ba042fd7
S: 6dd0ea5d8cfa10e89a052fbc90c864b63b10ac92 192.169.0.6:7006
   slots: (0 slots) slave
   replicates 70f9c714d051b11c3334ee1fe4c92712d7f8b485
[OK] All nodes agree about slots configuration.
>>> Check for open slots...
>>> Check slots coverage...
[OK] All 16384 slots covered.
root@c5de5c8e39a8:/data# 

```

继续查看redis集群

```bash
root@c5de5c8e39a8:/data# redis-cli -c -p 7007  -a  123456 cluster info
Warning: Using a password with '-a' or '-u' option on the command line interface may not be safe.
cluster_state:ok
cluster_slots_assigned:16384
cluster_slots_ok:16384
cluster_slots_pfail:0
cluster_slots_fail:0
cluster_known_nodes:6
cluster_size:3
cluster_current_epoch:6
cluster_my_epoch:2
cluster_stats_messages_ping_sent:100
cluster_stats_messages_pong_sent:99
cluster_stats_messages_meet_sent:1
cluster_stats_messages_sent:200
cluster_stats_messages_ping_received:99
cluster_stats_messages_pong_received:101
cluster_stats_messages_received:200
root@c5de5c8e39a8:/data# 

root@c5de5c8e39a8:/data#  redis-cli -c -p 7007 -a 123456 cluster nodes
Warning: Using a password with '-a' or '-u' option on the command line interface may not be safe.
71012eb26db37f5417182131d58e3905ba042fd7 192.169.0.4:7004@17004 master - 0 1616393280538 3 connected 10923-16383
2f172f56723c164a0ed9c6fdae575d3469e2c8b0 192.169.0.5:7005@17005 slave 71012eb26db37f5417182131d58e3905ba042fd7 0 1616393280739 3 connected
911f7ce08e901b298fd5661335b81499836b2f35 192.169.0.7:7007@17007 myself,slave bec09129ce7f7df0937b711bf42c22c8740ad868 0 1616393280000 2 connected
bec09129ce7f7df0937b711bf42c22c8740ad868 192.169.0.3:7003@17003 master - 0 1616393280233 2 connected 5461-10922
70f9c714d051b11c3334ee1fe4c92712d7f8b485 192.169.0.2:7002@17002 master - 0 1616393281140 1 connected 0-5460
6dd0ea5d8cfa10e89a052fbc90c864b63b10ac92 192.169.0.6:7006@17006 slave 70f9c714d051b11c3334ee1fe4c92712d7f8b485 0 1616393281240 1 connected

root@c5de5c8e39a8:/data# redis-cli -c -p 7007 -a 123456
127.0.0.1:7007> get name
-> Redirected to slot [5798] located at 192.169.0.3:7003
"ganx"
192.169.0.3:7003> 

```

进入redis集群

集群启动

redis-cli -c -p 7007  -a  123456

如果不是集群启动，只是进入单redis节点

redis-cli -p 7007  -a  123456

在执行命令get set 报错：(error) MOVED 6918 192.169.0.3:7003

```
root@c5de5c8e39a8:/data# redis-cli  -p 7007 -a 123456
Warning: Using a password with '-a' or '-u' option on the command line interface may not be safe.
127.0.0.1:7007> get name 
(error) MOVED 5798 192.169.0.3:7003
127.0.0.1:7007> get test
(error) MOVED 6918 192.169.0.3:7003
```

原理：节点会对命令请求进行分析和key的slot计算，并且会查找这个命令所要处理的键所在的槽。如果要查找的哈希槽正好就由接收到命令的节点负责处理， 那么节点就直接执行这个命令。

另一方面， 如果所查找的槽不是由该节点处理的话， 节点将查看自身内部所保存的哈希槽到节点 ID 的映射记录， 并向客户端回复一个 MOVED 错误。上面的错误信息包含键 x 所属的哈希槽15495， 以及负责处理这个槽的节点的 IP 和端口号 127.0.0.1:7003 。

虽然我们用Node ID来标识集群中的节点， 但是为了让客户端的转向操作尽可能地简单， 节点在 MOVED 错误中直接返回目标节点的 IP 和端口号， 而不是目标节点的 ID 。客户端应该记录槽15495由节点127.0.0.1:7003负责处理“这一信息， 这样当再次有命令需要对槽15495执行时， 客户端就可以加快寻找正确节点的速度。这样，当集群处于稳定状态时，所有客户端最终都会保存有一个哈希槽至节点的映射记录，使得集群非常高效： 客户端可以直接向正确的节点发送命令请求， 无须转向、代理或者其他任何可能发生单点故障（single point failure）的实体（entiy）。



测试：新增节点容器

```bash
# 运行节点容器
for port in `seq 7008 7009`; do 
docker run -it -p ${port}:${port} \
--restart always \
--name=redis-${port} \
--net redis  \
--privileged=true  \
-v `pwd`/node-${port}/conf/redis.conf:/usr/local/etc/redis/redis.conf \
-v  `pwd`/node-${port}/data:/data  \
-d redis:latest redis-server /usr/local/etc/redis/redis.conf; 
 done
```

结果：

```bash
[root@localhost redis]# ls
node-7002  node-7003  node-7004  node-7005  node-7006  node-7007
[root@localhost redis]# for port in `seq 7008 7009`; do 
> docker run -it -p ${port}:${port} \
> --restart always \
> --name=redis-${port} \
> --net redis  \
> --privileged=true  \
> -v `pwd`/node-${port}/conf/redis.conf:/usr/local/etc/redis/redis.conf \
> -v  `pwd`/node-${port}/data:/data  \
> -d redis:latest redis-server /usr/local/etc/redis/redis.conf; 
>  done
98666e61e8d93d57e940f4da60cea647e6a8ce359b27e01fe76bcd9318c05b04
56486c890cbba95fc384a46de1579e2c60f41297e0be516b757381db1907076e
[root@localhost redis]# docker ps -a
CONTAINER ID        IMAGE                 COMMAND                  CREATED             STATUS              PORTS                                                                                                         NAMES
56486c890cbb        redis:latest          "docker-entrypoint..."   5 seconds ago       Up 3 seconds        6379/tcp, 0.0.0.0:7009->7009/tcp                                                                              redis-7009
98666e61e8d9        redis:latest          "docker-entrypoint..."   12 seconds ago      Up 5 seconds        6379/tcp, 0.0.0.0:7008->7008/tcp                                                                              redis-7008
c5de5c8e39a8        redis:latest          "docker-entrypoint..."   47 hours ago        Up 47 hours         6379/tcp, 0.0.0.0:7007->7007/tcp                                                                              redis-7007
0f547721f80b        redis:latest          "docker-entrypoint..."   47 hours ago        Up 47 hours         6379/tcp, 0.0.0.0:7006->7006/tcp                                                                              redis-7006
5a4036b86f5e        redis:latest          "docker-entrypoint..."   47 hours ago        Up 47 hours         6379/tcp, 0.0.0.0:7005->7005/tcp                                                                              redis-7005
0ba0be6e56d0        redis:latest          "docker-entrypoint..."   47 hours ago        Up 47 hours         6379/tcp, 0.0.0.0:7004->7004/tcp                                                                              redis-7004
79029f8179d5        redis:latest          "docker-entrypoint..."   47 hours ago        Up 47 hours         6379/tcp, 0.0.0.0:7003->7003/tcp                                                                              redis-7003
e6d68d9d981c        redis:latest          "docker-entrypoint..."   47 hours ago        Up 47 hours         6379/tcp, 0.0.0.0:7002->7002/tcp                                                                              redis-7002
d2bf8d48879e        rabbitmq:management   "docker-entrypoint..."   2 months ago        Up 6 weeks          4369/tcp, 5671/tcp, 0.0.0.0:5672->5672/tcp, 15671/tcp, 15691-15692/tcp, 25672/tcp, 0.0.0.0:15672->15672/tcp   rabbitMq

```

新增主节点（先登录其中一个节点，连接到该节点）

说明：为一个指定集群添加节点，需要先连到该集群的任意一个节点IP（172.18.0.7:7007），再把新节点加入。该2个参数的顺序有要求：新加入的节点放前

异常：

```bash
root@0f547721f80b:/data# redis-cli -c -p 7006 -a 123456 --cluster add-node 192.169.0.8:7008 192.169.0.6:7006
Warning: Using a password with '-a' or '-u' option on the command line interface may not be safe.
>>> Adding node 192.169.0.8:7008 to cluster 192.169.0.6:7006
>>> Performing Cluster Check (using node 192.169.0.6:7006)
S: 6dd0ea5d8cfa10e89a052fbc90c864b63b10ac92 192.169.0.6:7006
   slots: (0 slots) slave
   replicates 70f9c714d051b11c3334ee1fe4c92712d7f8b485
M: bec09129ce7f7df0937b711bf42c22c8740ad868 192.169.0.3:7003
   slots:[5461-10922] (5462 slots) master
   1 additional replica(s)
S: 911f7ce08e901b298fd5661335b81499836b2f35 192.169.0.7:7007
   slots: (0 slots) slave
   replicates bec09129ce7f7df0937b711bf42c22c8740ad868
M: 71012eb26db37f5417182131d58e3905ba042fd7 192.169.0.4:7004
   slots:[10923-16383] (5461 slots) master
   1 additional replica(s)
S: 2f172f56723c164a0ed9c6fdae575d3469e2c8b0 192.169.0.5:7005
   slots: (0 slots) slave
   replicates 71012eb26db37f5417182131d58e3905ba042fd7
M: 70f9c714d051b11c3334ee1fe4c92712d7f8b485 192.169.0.2:7002
   slots:[0-5460] (5461 slots) master
   1 additional replica(s)
[OK] All nodes agree about slots configuration.
>>> Check for open slots...
>>> Check slots coverage...
[OK] All 16384 slots covered.
Could not connect to Redis at 192.169.0.8:7008: Connection refused
[ERR] Sorry, can't connect to node 192.169.0.8:7008

```

正常的应该是：

这里是在挂载的conf文件没有改对应的映射端口号

```bash
root@e6d68d9d981c:/data# redis-cli -c -p 7002 -a 123456 --cluster add-node 192.169.0.8:7008 192.169.0.2:7002
Warning: Using a password with '-a' or '-u' option on the command line interface may not be safe.
>>> Adding node 192.169.0.8:7008 to cluster 192.169.0.2:7002
>>> Performing Cluster Check (using node 192.169.0.2:7002)
M: 70f9c714d051b11c3334ee1fe4c92712d7f8b485 192.169.0.2:7002
   slots:[0-5460] (5461 slots) master
   1 additional replica(s)
M: bec09129ce7f7df0937b711bf42c22c8740ad868 192.169.0.3:7003
   slots:[5461-10922] (5462 slots) master
   1 additional replica(s)
S: 911f7ce08e901b298fd5661335b81499836b2f35 192.169.0.7:7007
   slots: (0 slots) slave
   replicates bec09129ce7f7df0937b711bf42c22c8740ad868
M: 71012eb26db37f5417182131d58e3905ba042fd7 192.169.0.4:7004
   slots:[10923-16383] (5461 slots) master
   1 additional replica(s)
S: 2f172f56723c164a0ed9c6fdae575d3469e2c8b0 192.169.0.5:7005
   slots: (0 slots) slave
   replicates 71012eb26db37f5417182131d58e3905ba042fd7
S: 6dd0ea5d8cfa10e89a052fbc90c864b63b10ac92 192.169.0.6:7006
   slots: (0 slots) slave
   replicates 70f9c714d051b11c3334ee1fe4c92712d7f8b485
[OK] All nodes agree about slots configuration.
>>> Check for open slots...
>>> Check slots coverage...
[OK] All 16384 slots covered.
>>> Send CLUSTER MEET to node 192.169.0.8:7008 to make it join the cluster.
[OK] New node added correctly.
```

#192.169.0.8 已经是master节点

```bash
root@e6d68d9d981c:/data# redis-cli -c -p 7002 -a 123456 cluster nodes
Warning: Using a password with '-a' or '-u' option on the command line interface may not be safe.
bec09129ce7f7df0937b711bf42c22c8740ad868 192.169.0.3:7003@17003 master - 0 1616563205000 2 connected 5461-10922
911f7ce08e901b298fd5661335b81499836b2f35 192.169.0.7:7007@17007 slave bec09129ce7f7df0937b711bf42c22c8740ad868 0 1616563206050 2 connected
c47b6e621425e19f0f9053948ca7a7dd98d45185 192.169.0.8:7008@17008 master - 0 1616563204536 0 connected
71012eb26db37f5417182131d58e3905ba042fd7 192.169.0.4:7004@17004 master - 0 1616563205041 3 connected 10923-16383
70f9c714d051b11c3334ee1fe4c92712d7f8b485 192.169.0.2:7002@17002 myself,master - 0 1616563205000 1 connected 0-5460
2f172f56723c164a0ed9c6fdae575d3469e2c8b0 192.169.0.5:7005@17005 slave 71012eb26db37f5417182131d58e3905ba042fd7 0 1616563206050 3 connected
6dd0ea5d8cfa10e89a052fbc90c864b63b10ac92 192.169.0.6:7006@17006 slave 70f9c714d051b11c3334ee1fe4c92712d7f8b485 0 1616563204536 1 connected
root@e6d68d9d981c:/data# 
```



新增从节点（这里的插曲，）

```bash
# 16a5ada35a648750daabdb91afa54ba6c62a26c4 是主节点的 Node-ID  如果不指定 --cluster-master-id 会随机分配到任意一个主节点。
redis-cli -c  -a China --cluster add-node 172.18.0.9:7009 172.18.0.7:7007 --cluster-slave --cluster-master-id  16a5ada35a648750daabdb91afa54ba6c62a26c4
```

异常：

```bash
root@95b3ac9115fb:/data# redis-cli -c -p 7008 -a 123456 --cluster add-node 192.168.0.9:7009 192.168.0.8:7008 --cluster-slave --cluster-master-id c47b6e621425e19f0f9053948ca7a7dd98d45185
Warning: Using a password with '-a' or '-u' option on the command line interface may not be safe.
>>> Adding node 192.168.0.9:7009 to cluster 192.168.0.8:7008
Could not connect to Redis at 192.168.0.8:7008: Connection timed out
```





故障：（断电导致集群不可用）

```bash
root@e6d68d9d981c:/data# redis-cli -c -p 7002 -a 123456 cluster info
Warning: Using a password with '-a' or '-u' option on the command line interface may not be safe.
cluster_state:fail
cluster_slots_assigned:16384
cluster_slots_ok:5128
cluster_slots_pfail:11256
cluster_slots_fail:0
cluster_known_nodes:7
cluster_size:4
cluster_current_epoch:7
cluster_my_epoch:1
cluster_stats_messages_ping_sent:6
cluster_stats_messages_sent:6
cluster_stats_messages_received:0
root@e6d68d9d981c:/data# 

```

解决：

```bash
缺失槽，执行脚本
for port in `seq 0 16384`; do 
	redis-cli -c -p 7006 -a 123456 cluster addslots ${port}; \
     done
```



```
集群命令
--cluster add-node 添加节点
--cluster info #查看集群信息
--cluster node #查看每个节点ip：port信息
```

