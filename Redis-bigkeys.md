1.自带命令redis-cli --bigkeys
该命令是redis自带，但是只能找出五种数据类型里最大的key。很明显，这并不能帮助我们去发现整个数据里的大key，所以一般不使用，执行后如下图：
![img](https://i.loli.net/2021/04/28/raRyFQO57cqC6js.png)



3.rdb_bigkeys工具
这是用go写的一款工具，分析rdb文件，找出文件中的大key，实测发现，不管是执行时间还是准确度都是很高的，一个3G左右的rdb文件，执行完大概两三分钟，直接导出到csv文件，方便查看，个人推荐使用该工具去查找大key。
工具地址：[ https://github.com/weiyanwei412/rdb_bigkeys](https://github.com/weiyanwei412/rdb_bigkeys)
编译方法：

```
mkdir /home/gocode/
export GOPATH=/home/gocode/
cd GOROOT
git clone https://github.com/weiyanwei412/rdb_bigkeys.git
cd rdb_bigkeys
go get 
go build
```

执行完成生成可执行文件rdb_bigkeys。
使用方法： ./rdb_bigkeys --bytes 1024 --file bigkeys.csv --sep 0 --sorted --threads 4 /home/redis/dump.rdb
/home/redis/dump.rdb修改为实际的文件路径
上述命令分析dump.rdb文件中大于1024bytes的KEY， 由大到小排好序， 以CSV格式把结果输出到bigkeys.csv的文件中，文件格式如图：
![img](https://img2020.cnblogs.com/blog/954825/202003/954825-20200306132427418-1363358030.png)
每列分别为数据库编号，key类型，key名，key大小，元素数量，最大值元素名，元素大小，key过期时间。