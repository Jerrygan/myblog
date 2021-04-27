ean定义信息->bean实例化（调用对象的构造方法实例化对象）->填充bean的属性->调用bean的init-method









defaultLIstableBean

![image-20210422162937191](https://i.loli.net/2021/04/22/u4cgIkzQ72KHqvZ.png)

典型的单例double check

![image-20210422163245430](https://i.loli.net/2021/04/22/HyrIlQ2J8mqkfeN.png)

doCreateBean

通过递归的方式获取目标bean及其所依赖的bean的；

![image-20210423151631593](https://i.loli.net/2021/04/23/M1p8ajIEqfJLZDT.png)

![image-20210423151751571](https://i.loli.net/2021/04/23/kOYoearGsf8wTj7.png)

实例化->

![image-20210423143555250](https://i.loli.net/2021/04/23/zkXpTCU6lcvFA1w.png)

允许多个BeanDefinitionPostProcessor去修改bean definition

![image-20210423143856564](https://i.loli.net/2021/04/23/XVvQ6OPRSDA12Mn.png)

![image-20210423143909306](https://i.loli.net/2021/04/23/mw8PlKLp3d1zyOh.png)

中间解决循环依赖

了解这些

![image-20210423144259369](https://i.loli.net/2021/04/23/2T8K9D7RXjxpuYw.png)

将还未初始化的bean放入三级缓存beanSingletonFactory

![image-20210423143945286](https://i.loli.net/2021/04/23/DkpxyueZ5m34vBI.png)

![image-20210423144107460](https://i.loli.net/2021/04/23/I9Es43QT7uRajNe.png)

初始化过程

填充bean属性

```
Populate the bean instance in the given BeanWrapper with the property values
```

![image-20210423144558816](https://i.loli.net/2021/04/23/ixRBFpt2kId1Hqe.png)

初始化bean

提供对外接口InitializingBean

```
/**
	 * Initialize the given bean instance, applying factory callbacks
	 * as well as init methods and bean post processors.
	 * <p>Called from {@link #createBean} for traditionally defined beans,
	 * and from {@link #initializeBean} for existing bean instances.
	 * @param beanName the bean name in the factory (for debugging purposes)
	 * @param bean the new bean instance we may need to initialize
	 * @param mbd the bean definition that the bean was created with
	 * (can also be {@code null}, if given an existing bean instance)
	 * @return the initialized bean instance (potentially wrapped)
	 * @see BeanNameAware
	 * @see BeanClassLoaderAware
	 * @see BeanFactoryAware
	 * @see #applyBeanPostProcessorsBeforeInitialization
	 * @see #invokeInitMethods
	 * @see #applyBeanPostProcessorsAfterInitialization
	 */
```

![image-20210423145151661](https://i.loli.net/2021/04/23/pwm1iOx75lGoVtF.png)

![image-20210423145343738](https://i.loli.net/2021/04/23/WOHVhCD9I1wjXAS.png)

将完全的bean加入销毁beanlist

提供对外接口DisposableBean

```
Add the given bean to the list of disposable beans in this factory,
* registering its DisposableBean interface and/or the given destroy method
* to be called on factory shutdown (if applicable). Only applies to singletons.
```

![image-20210423150639592](https://i.loli.net/2021/04/23/AwqTSOthmscQDoK.png)

![image-20210423145803224](https://i.loli.net/2021/04/23/9e6qmTIZpKVSgbs.png)

![image-20210423150605472](https://i.loli.net/2021/04/23/woj4zbJmUC8SWGx.png)



这里抛出问题，如果我们直接将提前曝光的对象放到二级缓存earlySingletonObjects，Spring循环依赖时直接取就可以解决循环依赖了，为什么还要三级缓存singletonFactory然后再通过getObject()来获取呢？这不是多此一举？

提前对bean添加beanPostProcessor，曝光的时候并不调用该后置处理器，只有曝光，且被提前引用的时候才调用，确保了被提前引用这个时机触发。

![image-20210423152940105](https://i.loli.net/2021/04/23/AImbTHD36xSjZsn.png)

![image-20210423153120677](https://i.loli.net/2021/04/23/63ZziPolGwBnspm.png)



**为什么Spring不能解决构造器的循环依赖？**

​     因为构造器是在实例化时调用的，此时bean还没有实例化完成，如果此时出现了循环依赖，一二三级缓存并没有Bean实例的任何相关信息，在实例化之后才放入三级缓存中，因此当getBean的时候缓存并没有命中，这样就抛出了循环依赖的异常了。

这文章写得好

https://www.freesion.com/article/62151334702/