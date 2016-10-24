package com.rk.coursedetails;

import javax.persistence.EntityManager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication

public class CourseDetailService1Application {

	public static void main(String[] args) {
		System.out.println("lllllllllllll " +EntityManager.class.getProtectionDomain()
                .getCodeSource()
                .getLocation());
		SpringApplication.run(CourseDetailService1Application.class, args);
	}
}
