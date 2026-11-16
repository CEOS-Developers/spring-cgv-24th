package com.ceos24.cgv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class CgvApplication {

	// 상영 시각·선점 만료·결제 승인 시각을 시간대 없는 LocalDateTime으로 다루므로 JVM 기본 시간대가 곧 업무 시간대다.
	// EC2·컨테이너는 기본이 UTC라 실행 환경에 맡기면 비교가 9시간 어긋난다. Clock 빈과 JPA Auditing이 이 값을 따른다.
	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
		SpringApplication.run(CgvApplication.class, args);
	}

}
