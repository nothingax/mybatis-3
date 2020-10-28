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

import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PluginTest {

  @Test
  void mapPluginShouldInterceptGet() {
    Map map = new HashMap();
    map = (Map) new AlwaysMapPlugin().plugin(map);
    assertEquals("Always", map.get("Anything"));
  }

  /**
   * plugin 是对原对象(比如parameterHandler)的代理，基于jdk动态代理（基于接口）,代理对象中加入了拦截器,每多一个拦截器多包一层，层层代理。
   * 目标对象+拦截器1 —>代理对象1，代理对象1+拦截器2 ->代理对象2，代理对象2+拦截器3->代理对象3
   * 代理对象执行query方法，进入代理对象invoke方法，执行拦截器逻辑，再执行当前代理对象的目标对象的invoke（），这是个递归，最终所有拦截器形成了链式调用
   */
  @Test
  void interceptorUse() {
    Interceptor interceptor1 = new Interceptor() {
      @Override
      public Object intercept(Invocation invocation) throws Throwable {
        System.out.println("interceptor:11111 执行");
        return invocation.proceed();
      }
    };

    Interceptor interceptor2 = invocation -> {
      System.out.println("interceptor:222222  执行");
      return invocation.proceed();
    };

    // 最终被拦截的对象
    ParameterHandler target = new DefaultParameterHandler(null, null, null);

    // 动态代理 InvocationHandler的子类，并将目标对象和第一个拦截器放进去
    Plugin plugin1 = new Plugin(target, interceptor1, null);
    // 获得代理对象
    Object object1 = Proxy.newProxyInstance(target.getClass().getClassLoader(), target.getClass().getInterfaces(), plugin1);

    // 动态代理 InvocationHandler的子类，并将目标对象和第一个拦截器放进去
    Plugin plugin2 = new Plugin(object1, interceptor2, null);
    // 获得代理对象
    Object object2 = Proxy.newProxyInstance(object1.getClass().getClassLoader(), object1.getClass().getInterfaces(), plugin2);

  }


  @Test
  void shouldNotInterceptToString() {
    Map map = new HashMap();
    map = (Map) new AlwaysMapPlugin().plugin(map);
    assertNotEquals("Always", map.toString());
  }

  @Intercepts({
      @Signature(type = Map.class, method = "get", args = {Object.class})})
  public static class AlwaysMapPlugin implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) {
      return "Always";
    }

  }

}
