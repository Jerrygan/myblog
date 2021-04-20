package com.joysim.core.util;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.util.concurrent.RateLimiter;
/**
 * ip接口限流工具类
 * @author ganx
 * @date 2020年11月16日 上午11:20:27
 */
public class IpRequestLimitUtil {
	private static final Logger logger = LoggerFactory.getLogger(IpRequestLimitUtil.class);

	
	// CacheLoader key作为IP地址， value 作为RateLimiter对像
	private static LoadingCache<String, RateLimiter> ipRequestCaches = 
			//CacheBuilder的构造函数是私有的，只能通过其静态方法newBuilder()来获得CacheBuilder的实例
			CacheBuilder.newBuilder()
			//设置缓存最大容量为100，超过100之后就会按照LRU最近虽少使用算法来移除缓存项
			.maximumSize(1000)
			.expireAfterAccess(2, TimeUnit.MINUTES)
			//设置缓存的移除通知
            .removalListener(new RemovalListener<Object, Object>() {
                @Override
                public void onRemoval(RemovalNotification<Object, Object> notification) {
                	logger.info("{} was removed, cause is {}", notification.getKey(), notification.getCause());
                }
            })
			//build方法中可以指定CacheLoader，在缓存不存在时通过CacheLoader的实现自动加载缓存
			.build(new CacheLoader<String, RateLimiter>() {
				@Override
				public RateLimiter load(String key) throws Exception {
					return RateLimiter.create(0.1);
				}
			})
			;

	/**
	 * 限制接口每秒的可获得令牌数
	 * @param ipAddr ip地址
	 * @param limit 限制令牌数
	 * @return
	 * @throws ExecutionException
	 */
	public static RateLimiter getIPLimiter(String ipAddr, Double limit) throws ExecutionException {
		ipRequestCaches.put(ipAddr, RateLimiter.create(limit));
		return getIPLimiter(ipAddr);
	}
	
	public static RateLimiter getIPLimiter(String ipAddr) throws ExecutionException {
		return ipRequestCaches.get(ipAddr);
	}

}
