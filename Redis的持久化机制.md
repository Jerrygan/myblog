# Redis的持久化机制

理解持久化，就是把内存的数据写到磁盘，防止服务宕机导致内存数据的丢失

## RDB

指定时间间隔内对数据进行快照存储，即是将内存的数据写入磁盘，也就是 Snapshot 快照；恢复时将快照文件写入磁盘

### conf 配置

dump.rdb二进制文件保存着Redis数据库快照

conf 文件配置达到快照持久化条件，自动保存一次数据集

```
save 900 1 #达到条件“900秒内有至少有1个key被改动”， 自动保存一次数据集
save 300 10 #达到条件“300秒内有至少有10个key被改动”， 自动保存一次数据集
save 60 10000 #达到条件“60秒内有至少有10000个key被改动”， 自动保存一次数据集
```

```
save "" #注释掉其他save行，即关闭持久化
```

我们也可以通过调用 save或者 bgsave， 手动让 Redis 进行数据集保存操作。

- save
  很少在生产使用该命令，因为会阻塞所有的客户端请求，此时 Redis 不能处理其他命令，直到RDB持久化过程完成；用 bgsave 命令代替。基本上 Redis 内部所有的RDB操作都是采用 bgsave 命令。
- bgsave
  异步后台保存数据集，执行

### bgsave 原理

Redis会单独创建一个（fork）子进程进行持久化，并将内存数据写入一个临时文件中，待数据写入完成，替换掉原来的RDB文件。

继续介绍 conf 配置文件

```
stop-writes-on-bgsave-error yes #启用RDB并且最后一次后台保存数据集时失败，Redis是否停止接受数据，默认yes；这样数据即没有正确持久化到磁盘
rdbcompression yes #是否对RDB文件进行压缩，默认yes；如果不想消耗CPU来进行压缩的话，可以设置为关闭此功能，但是存储在磁盘上的快照会比较大。
rdbchecksum yes #存储快照后是否进行数据校验，默认yes
dbfilename dump.rdb #RDB文件的命名
dir ./ #设置快照文件的存放路径
```

### 恢复数据

将备份文件 (dump.rdb) 放在 conf 文件dir指定路径下，并启动服务即可，Redis就会自动加载文件数据至内存了。Redis 服务器在载入 RDB 文件期间，会一直处于阻塞状态，直到载入工作完成为止

### 总结

- RDB按设置规则保存某个时间点的数据集，在恢复数据方面，可以具体到恢复某个时间版本的数据集！

- 对数据完整性要求不高，很适合大规模的数据恢复！

- 需要一定的时间间隔的进程操作，如果Redis宕机，会丢失最后一次修改的数据集！


## AOF

AOF 文件是一个只进行追加的日志文件

### conf 配置

```java
appendonly no #Redis默认使用的是RDB持久化，是否开启aof，默认是no，不开启

appendfilename appendonly.aof #aof文件名

#aof持久化策略的3种配置    
# appendfsync always #总是执行fsync，每次写入数据集都执行，保证同步到磁盘，效率最低下
appendfsync everysec #每秒执行一次fsync，就算是意外宕机，也是丢失这1s数据，所以通常设置 everysec ，兼顾安全性和效率
# appendfsync no #不执行fsync，速度最快，可是不太安全

no-appendfsync-on-rewrite yes #Redis以aof重写文件或者写入RDB文件，会执行大量IO，如果设置appendfsync为everysec/always，执行fsync都会阻塞Redis处理请求，所以默认为no，即是重写文件不执行fsync，这里建议开启yes，因为如果Redis意外宕机，会丢失暂时保存在内存的数据
    
auto-aof-rewrite-percentage 100 #aof自动重写配置，这里是百分比设置，即是在aof文件超过上一次aof重写文件的百分之多少就会重写，100即是2倍
    
auto-aof-rewrite-min-size 64mb #允许重写aof文件最小大小，跟auto-aof-rewrite-percentage是&关系，两者满足才自动重写aof文件
```

### 实现原理

AOF 文件重写并不是对原文件进行重新整理，而是直接读取服务器现有的键值对，然后用一条命令去代替之前记录这个键值对的多条命令，生成一个新的文件后去替换原来的 AOF 文件。

- Redis 执行 fork() ，现在同时拥有父进程和子进程。

- 子进程开始将新 AOF 文件的内容写入到临时文件。
- 对于所有新执行的写入命令，父进程一边将它们累积到一个内存缓冲区中，一边将这些改动追加到现有 AOF 文件的末尾,这样即使在重写的中途发生停机，现有的 AOF 文件也还是安全的。
- 当子进程完成重写工作时，它给父进程发送一个信号，父进程在接收到信号之后，将内存缓冲区中的所有数据追加到新 AOF 文件的末尾。
- 最后 Redis 原子性地用新文件替换旧文件，之后所有命令都会直接追加到新 AOF 文件的末尾。

### 恢复数据

开启了AOF ，重启 Redis 之后就会进行 AOF 文件的载入。

异常修复命令：redis-check-aof --fix 进行修复

### 总结

- AOF 是有序地保存对 Redis 数据库的所有写入操作！
- 对于相同的数据集来说，AOF 文件的体积通常要大于 RDB 文件的体积！
- 根据所使用的 fsync 策略，AOF 的速度可能会慢于 RDB ！