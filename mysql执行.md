



## mysql执行顺序



## explain解析

### id列

![image-20210429104852886](https://i.loli.net/2021/04/29/qnIop5Z6bOuwWsQ.png)

![image-20210429105220581](https://i.loli.net/2021/04/29/oHzUetIL34SWcYs.png)

![image-20210429105433444](https://i.loli.net/2021/04/29/J7ZYHeauT4jXRDo.png)

id列相同，按顺序执行

id列递增，包含子查询，先执行序号大的

id列出现相同且不同，先执行序号大的，同序号的按顺序执行

### select_type

![image-20210429110129271](https://i.loli.net/2021/04/29/wKEPNu65LdFfOVX.png)

SIMPLE：简单的查询

图例1

![image-20210429110701365](https://i.loli.net/2021/04/29/V4sXmbNf1UlHeon.png)

PRIMARY：出现子查询，最外层的查询为PRIMARY

SUBQUERY：子查询，出现在SELECT、WHERE的

DERIVED：在FROM列表中包含的子查询被标记为DERIVED（衍生），MySQL会递归执行这些子查询，把结果放在临时表中

### table

查询的表或者派生表<derived2> 

如图例1，先执行子查询，查询oil_code表的记录，再将其写入临时表p

### type

常用的类型

**system** 

表只有一行记录（等于系统表），这是 const 类型的特列，平时不会出现，这个也可以忽略不计 

**const** 

表示通过索引一次就找到了,const 用于比较 primary key 或者 unique 索引。因为只匹配一行数据，所以很快 

如将主键置于 where 列表中，MySQL 就能将该查询转换为一个常量。

**eq_ref** 

唯一性索引扫描，对于每个索引键，表中只有一条记录。常见于主键或唯一索引扫描。

**ref** 

非唯一性索引扫描，返回匹配某个单独值的所有行，本质上也是一种索引访问，它返回所有匹配某个单独值的行， 会找到多个符合条件的行。

**range** 

只检索给定范围的行，使用一个索引来选择行。一般就是在你的 where 语句中出现了索引列字段使用了 between、<、>、in 等，查询这种范围扫描索引扫描比全表扫描要好

**index** 

出现index是sql使用了索引但是没用通过索引进行过滤，一般是使用了覆盖索引或者是利用索引进行了排序分组

**all** 

全表遍历寻找匹配行

##### 性能

system > const > eq_ref > ref >  range > index > all

一般来说，得保证查询至少达到 range 级别，最好能达到 ref。

### possible_keys

可能定位到的索引，但不一定实际被使用到

### key

实际使用的索引。如果为NULL，则没有使用索引

### key_len

表示索引中使用的字节数，可通过该列计算查询中使用的索引的长度

### ref

哪些列或常量被用于查找索引列上的值

### key

实际使用到的索引

### key_len

### rows

rows 列显示 MySQL 认为它执行查询时必须检查的行数。这里越少越好！

### Extra

**Using filesort** 

 mysql 会对数据使用一个外部的索引排序，而不是按照表内的索引顺序进行读取。MySQL 中无法利用索引，完成的排序操作称为“文件排序”。

**Using temporary** 

使了用临时表保存中间结果,MySQL 在对查询结果排序时使用临时表。常见于排序 order by 和分组查询 group by

**Using index** 

表示相应的 select 操作中使用了覆盖索引(Covering Index)，避免访问了回表！ 

如果同时出现 using where，表明索引被用来执行索引键值的查找;如果没有同时出现 using where，表明索引只是用来读取数据而非利用索引执行查找。 

利用索引进行了排序或分组。

**Using where** 

表明使用了 where 过滤。

**Using join buffer**

使用了连接缓存。

**impossible where** 

where 子句的值总是 false，不能用来获取任何元组。

