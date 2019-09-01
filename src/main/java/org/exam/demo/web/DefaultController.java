package org.exam.demo.web;


import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
public class DefaultController {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("redis")
    public String test() {
        ValueOperations<String, String> opsForValue = stringRedisTemplate.opsForValue();
        opsForValue.set("username", "xiejx618");
        String username = opsForValue.get("username");
        System.out.println("username = " + username);
        return "response";
    }


}
