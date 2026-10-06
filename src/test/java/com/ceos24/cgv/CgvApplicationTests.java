package com.ceos24.cgv;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        properties = {
            "database=cgv",
            "password=1234",
            "jwt-secret=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY3ODkw",
            "admin-token=admin"
        })
class CgvApplicationTests {

    @Test
    void contextLoads() {}
}
