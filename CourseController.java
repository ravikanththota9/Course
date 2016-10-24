package com.rk.coursedetails;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CourseController {

	@Autowired
	CourseRepository repo;
	
	@RequestMapping("/courses")
	public List<Courses> getAllCourses(){
		
		return repo.findAll();
		
	}
	
	@RequestMapping("/courses/{id}")
	public Courses getCourseById(@PathVariable Integer id){
		return (Courses) repo.findOne(id);
	}
}
