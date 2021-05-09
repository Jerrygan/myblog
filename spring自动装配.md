spring自动装配

思路

1、如何定位到主类

![image-20210426094608861](https://i.loli.net/2021/04/26/4HKLT7ACYEb9O5p.png)

2、如何解析主类上的注解，关键是@Import的

![image-20210426094435777](https://i.loli.net/2021/04/26/kLZnjY9OlTJV7xP.png)

![image-20210426094514885](https://i.loli.net/2021/04/26/wapibvdK6XOrFgm.png)

3、

![服务架构图](https://i.loli.net/2021/05/09/LRjPOmVQUyYlnWE.png)



源码导向

![image-20210426100335558](https://i.loli.net/2021/04/26/nchBaGivFRIKuQN.png)

![image-20210426100436543](https://i.loli.net/2021/04/26/QA3nGFNomCs4WzM.png)

![image-20210426100524391](https://i.loli.net/2021/04/26/jQTB6hc1mo2ruCM.png)

![image-20210426100707777](https://i.loli.net/2021/04/26/3lcynm5iKOM7HtX.png)

![image-20210426100819603](https://i.loli.net/2021/04/26/gZabeSGxW6mfAnu.png)

![image-20210426100925064](https://i.loli.net/2021/04/26/FwDIpaBjG5yKc7l.png)

![image-20210426101037925](https://i.loli.net/2021/04/26/fzJHt3kNuoUTlxy.png)

![image-20210426101107232](https://i.loli.net/2021/04/26/DtRQoNyfBngITwp.png)

获取@Import注解的类getImports

![image-20210427095736728](https://i.loli.net/2021/04/27/oytKazSBGpMcVAO.png)

递归实现

![image-20210427095832604](https://i.loli.net/2021/04/27/CdN2wO9Fj4Isv5Z.png)

![image-20210427102046117](https://i.loli.net/2021/04/27/SURdwAivy2G1O8M.png)



![image-20210426102151874](https://i.loli.net/2021/04/26/ngKaFYjMDs5JOil.png)

![image-20210427102329808](https://i.loli.net/2021/04/27/1ogR7PXICeWN8GD.png)

最后定位到了加载类EnableAutoConfiguration.class

![image-20210427102407973](https://i.loli.net/2021/04/27/WDXbJE8UHIwin29.png)

这里只截取了部分

你也可以自定义EnableAutoConfiguration包下的类

![image-20210427102625763](https://i.loli.net/2021/04/27/wQd5YXHCj9Pa4uF.png)