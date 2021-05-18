### 区别1 原始构成

```java
public void doTest() {
    synchronized (this) {
        System.out.println(1);
    }
    new ReentrantLock();
}
```

synchronized是关键字，底层是monitor对象完成

MONITORENTER

MONITOREXIT 出现两次，第一个正常退出，第二个异常退出，保证不会出现死锁

![image-20210518231553620](https://i.loli.net/2021/05/19/Ct6rlV94LmsunkR.png)

Lock是具体类

![image-20210518231358268](https://i.loli.net/2021/05/19/QVK5eF1rgfa796H.png)

### 区别2 使用方法

synchronized不需要手动释放锁

ReentrantLock需要用户手动释放锁，如果没有主动释放锁，有可能导致死锁现象，lock()和unlock()一起使用

### 区别3 等待是否可中断

synchronized不可中断，除非抛出异常或者正常运行完成

Lock可中断，调用interrupt()方法可中断

查看Lock的方法

![image-20210518233130016](https://i.loli.net/2021/05/19/RNCakD3uW4ihbJr.png)

![image-20210518233158819](https://i.loli.net/2021/05/19/Y3o7DdQfc9tqbUK.png)

### 区别4 加锁是否公平

两者默认都是非公平锁

ReentrantLock内部实现公平锁、非公平锁，提供构造方法，可传入boolean值创建实例

![image-20210518233525048](https://i.loli.net/2021/05/19/HQ6yDz2lV1RrAs5.png)

### 区别5 锁绑定多个条件condition

synchronized没有，随机唤醒一个或者全部等待的线程

ReentrantLock可用来实现分组唤醒需要唤醒的线程（或者可以说是指定）

例如

实现需求：多线程A、B、C之间指定按顺序调用，实现A->B->C三个线程顺序唤醒，分别按顺序分5次打印数字0-4

```java
package com.gaozhu;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Product {
    //A 10 B 20 C 30
    private Integer num;

    Product(int num) {
        this.num = num;
    }

    private Lock lock = new ReentrantLock();
    private Condition condition1 = lock.newCondition();
    private Condition condition2 = lock.newCondition();
    private Condition condition3 = lock.newCondition();


    public void do10(){
        try {
            lock.lock();
            //判断
            while (num != 10){
                condition1.await();
            }
            //处理
            for (int i = 0; i < 5; i++) {
                System.out.println(Thread.currentThread().getName() + ":" + i);
            }
            //通知
            num = 20;
            condition2.signal();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    public void do20(){
        try {
            lock.lock();
            //判断
            while (num != 20){
                condition2.await();
            }
            //处理
            for (int i = 0; i < 5; i++) {
                System.out.println(Thread.currentThread().getName() + ":" + i);
            }
            //通知
            num = 30;
            condition3.signal();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    public void do30(){
        try {
            lock.lock();
            //判断
            while (num != 30){
                condition3.await();
            }
            //处理
            for (int i = 0; i < 5; i++) {
                System.out.println(Thread.currentThread().getName() + ":" + i);
            }
            //通知
            num = 10;
            condition1.signal();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }
}

```

```java
package com.gaozhu;

public class LockTest {
    public static void main(String[] args) {
        Product product = new Product(10);
        new Thread(()-> {
            for (int i = 0; i < 3; i++) {
                product.do10();
            }
        }, "A").start();
        new Thread(()-> {
            for (int i = 0; i < 3; i++) {
                product.do20();
            }
        }, "B").start();
        new Thread(()-> {
            for (int i = 0; i < 3; i++) {
                product.do30();
            }
        }, "C").start();
    }
}
```

