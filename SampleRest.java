package com.rk.coursedetails;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonView;

@RestController
public class SampleRest {
	
	@RequestMapping("/test")
	@JsonView
	public String test(){
		
		return "Test Sucess";
	}

}
