/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.aop.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator;
import org.springframework.aop.aspectj.autoproxy.AspectJAwareAdvisorAutoProxyCreator;
import org.springframework.aop.framework.autoproxy.InfrastructureAdvisorAutoProxyCreator;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;

/**
 * Utility class for handling registration of AOP auto-proxy creators.
 *
 * <p>Only a single auto-proxy creator can be registered yet multiple concrete
 * implementations are available. Therefore this class wraps a simple escalation
 * protocol, allowing classes to request a particular auto-proxy creator and know
 * that class, {@code or a subclass thereof}, will eventually be resident
 * in the application context.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @since 2.5
 * @see AopNamespaceUtils
 */
public abstract class AopConfigUtils {

	/**
	 * The bean name of the internally managed auto-proxy creator.
	 */
	public static final String AUTO_PROXY_CREATOR_BEAN_NAME =
			"org.springframework.aop.config.internalAutoProxyCreator";

	/**
	 * Stores the auto proxy creator classes in escalation order.
	 */
	private static final List<Class<?>> APC_PRIORITY_LIST = new ArrayList<Class<?>>();

	/**
	 * Setup the escalation list.
	 */
	static {
		APC_PRIORITY_LIST.add(InfrastructureAdvisorAutoProxyCreator.class);
		APC_PRIORITY_LIST.add(AspectJAwareAdvisorAutoProxyCreator.class);
		APC_PRIORITY_LIST.add(AnnotationAwareAspectJAutoProxyCreator.class);
	}


	public static BeanDefinition registerAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
		return registerAutoProxyCreatorIfNecessary(registry, null);
	}

	public static BeanDefinition registerAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry, Object source) {
		return registerOrEscalateApcAsRequired(InfrastructureAdvisorAutoProxyCreator.class, registry, source);
	}

	public static BeanDefinition registerAspectJAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
		return registerAspectJAutoProxyCreatorIfNecessary(registry, null);
	}

	public static BeanDefinition registerAspectJAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry, Object source) {
		return registerOrEscalateApcAsRequired(AspectJAwareAdvisorAutoProxyCreator.class, registry, source);
	}

	public static BeanDefinition registerAspectJAnnotationAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
		return registerAspectJAnnotationAutoProxyCreatorIfNecessary(registry, null);
	}

	/**
	 * 对于 AOP 的实现，基本上都是靠 AnnotationAwareAspectJAutoProxyCreator 去完成，它可以根据 @Point 注解定义的切点来
	 * 自动代理相匹配的 bean。但是为了配置简便，Spring使用了自定义的配置来帮助我们自动注册 AnnotationAwareAspectJAutoProxyCreator
	 * 其过程就是在这里实现的
	 */
	public static BeanDefinition registerAspectJAnnotationAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry, Object source) {
		return registerOrEscalateApcAsRequired(AnnotationAwareAspectJAutoProxyCreator.class, registry, source);
	}

	/**
	 * 强制使用的过程其实也是一个属性设置的过程，设置 beanDefinition 的 proxyTargetClass 为 true
	 */
	public static void forceAutoProxyCreatorToUseClassProxying(BeanDefinitionRegistry registry) {
		if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME)) {
			BeanDefinition definition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
			definition.getPropertyValues().add("proxyTargetClass", Boolean.TRUE);
		}
	}

	public static void forceAutoProxyCreatorToExposeProxy(BeanDefinitionRegistry registry) {
		if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME)) {
			BeanDefinition definition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
			definition.getPropertyValues().add("exposeProxy", Boolean.TRUE);
		}
	}

	/**
	 * 将 AnnotationAwareAspectJAutoProxyCreator 封装为一个BeanDefinition
	 */
	private static BeanDefinition registerOrEscalateApcAsRequired(Class<?> cls, BeanDefinitionRegistry registry, Object source) {
		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
		if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME)) {
			// 如果已经存在了自动代理创建器，并且已存在的自动代理创建器与现在的不一致，那么需要根据优先级来判断使用哪一个
			BeanDefinition apcDefinition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
			if (!cls.getName().equals(apcDefinition.getBeanClassName())) {
				int currentPriority = findPriorityForClass(apcDefinition.getBeanClassName());
				int requiredPriority = findPriorityForClass(cls);
				if (currentPriority < requiredPriority) {
					apcDefinition.setBeanClassName(cls.getName());
				}
			}
			return null;
		}
		RootBeanDefinition beanDefinition = new RootBeanDefinition(cls);
		beanDefinition.setSource(source);
		beanDefinition.getPropertyValues().add("order", Ordered.HIGHEST_PRECEDENCE);
		beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		registry.registerBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME, beanDefinition);
		return beanDefinition;
	}

	private static int findPriorityForClass(Class<?> clazz) {
		return APC_PRIORITY_LIST.indexOf(clazz);
	}

	private static int findPriorityForClass(String className) {
		for (int i = 0; i < APC_PRIORITY_LIST.size(); i++) {
			Class<?> clazz = APC_PRIORITY_LIST.get(i);
			if (clazz.getName().equals(className)) {
				return i;
			}
		}
		throw new IllegalArgumentException(
				"Class name [" + className + "] is not a known auto-proxy creator class");
	}

}
