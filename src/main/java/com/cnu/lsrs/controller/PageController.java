package com.cnu.lsrs.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 공통 화면. 기능별 화면 컨트롤러는 각 담당자가 따로 만든다. */
@Controller
public class PageController {

	@GetMapping("/")
	public String home() {
		return "index";
	}
}
