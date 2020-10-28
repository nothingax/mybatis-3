/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.plugin;

import org.apache.ibatis.reflection.ExceptionUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Clinton Begin
 */
public class Plugin implements InvocationHandler {

  private final Object target;
  private final Interceptor interceptor;
  private final Map<Class<?>, Set<Method>> signatureMap;

  public Plugin(Object target, Interceptor interceptor, Map<Class<?>, Set<Method>> signatureMap) {
    this.target = target;
    this.interceptor = interceptor;
    this.signatureMap = signatureMap;
  }

  public static Object wrap(Object target, Interceptor interceptor) {
    Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);
    Class<?> type = target.getClass();
    // 被从代理对象实现的接口中，只过滤出拦截器注解定义的需要拦截的接口
    Class<?>[] interfaces = getAllInterfaces(type, signatureMap);
    if (interfaces.length > 0) {
      // 代理后的对象
      Object newTarget = Proxy.newProxyInstance(
        type.getClassLoader(),
        interfaces,
        new Plugin(target, interceptor, signatureMap));
      return newTarget;
    }
    return target;
  }

  /**
   * 目标对象加入interceptor 增强逻辑后的生成的代理对象在执行方法使，会进入此invoke方法
   *
   * @param proxy
   * @param method
   * @param args
   * @return
   * @throws Throwable
   */
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      // method.getDeclaringClass()获取的是 method所在的类/接口的class
      Set<Method> methods = signatureMap.get(method.getDeclaringClass());

      // 如果当前执行的这个method在拦截器注解的方法中，则执行interceptor拦截
      if (methods != null && methods.contains(method)) {
        // 代理对象执行query方法，进入代理对象invoke方法，执行拦截器逻辑，再执行当前代理对象的目标对象的invoke（），这是个递归，最终所有拦截器形成了链式调用
        return interceptor.intercept(new Invocation(target, method, args));
      }
      return method.invoke(target, args);
    } catch (Exception e) {
      throw ExceptionUtil.unwrapThrowable(e);
    }
  }

  private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
    Intercepts interceptsAnnotation = interceptor.getClass().getAnnotation(Intercepts.class);
    // issue #251
    if (interceptsAnnotation == null) {
      throw new PluginException("No @Intercepts annotation was found in interceptor " + interceptor.getClass().getName());
    }
    Signature[] sigs = interceptsAnnotation.value();
    Map<Class<?>, Set<Method>> signatureMap = new HashMap<>();
    for (Signature sig : sigs) {
      Set<Method> methods = signatureMap.computeIfAbsent(sig.type(), k -> new HashSet<>());
      try {
        Method method = sig.type().getMethod(sig.method(), sig.args());
        methods.add(method);
      } catch (NoSuchMethodException e) {
        throw new PluginException("Could not find method on " + sig.type() + " named " + sig.method() + ". Cause: " + e, e);
      }
    }
    return signatureMap;
  }

  /**
   * 找出需要拦截的目标对象的接口
   *
   * 例子
   * @Intercepts({
   *         @Signature(type = Executor.class,
   *                 method = "query",
   *                 args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
   *
   *         @Signature(type = Executor.class,
   *                 method = "query",
   *                 args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
   * })
   *
   *
   * @param type 被代理的目标对象的类 ，比如 {@code parameterHandler.getClass()}
   * @param signatureMap Map<Class<?>, Set<Method>> signatureMap,  key 是Intercepts注解里的Signature注解里的type
   *                     value 是根据注解里的的Signature里的类、方法名、参数类型，得到的method列表
   * @return
   */
  private static Class<?>[] getAllInterfaces(Class<?> type, Map<Class<?>, Set<Method>> signatureMap) {
    // 找出需要拦截的目标对象的接口
    Set<Class<?>> interfaces = new HashSet<>();
    while (type != null) {
      for (Class<?> c : type.getInterfaces()) {
        if (signatureMap.containsKey(c)) {
          interfaces.add(c);
        }
      }
      type = type.getSuperclass();
    }
    return interfaces.toArray(new Class<?>[interfaces.size()]);
  }

}
