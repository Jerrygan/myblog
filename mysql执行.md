



## mysql执行顺序

### explain解析

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

### possible_keys

可能定位到的索引

### key

实际使用到的索引

### key_len

索引长度

### rows

搜索行数

### Extra



