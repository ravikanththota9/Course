package com.rk.coursedetails;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
public class Courses {
	
	@Id @GeneratedValue(strategy=GenerationType.AUTO)
	private int id;
	private String COURSENAME;
	private String COURSETYPE;
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	
	public String getCOURSENAME() {
		return COURSENAME;
	}
	public void setCOURSENAME(String cOURSENAME) {
		COURSENAME = cOURSENAME;
	}
	public String getCOURSETYPE() {
		return COURSETYPE;
	}
	public void setCOURSETYPE(String cOURSETYPE) {
		COURSETYPE = cOURSETYPE;
	}
	
	
	

}
