package com.hmdp.controller;


import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog-comments")
@ConditionalOnProperty(name = "legacy.hmdp.endpoints-enabled", havingValue = "true")
public class BlogCommentsController {

}
