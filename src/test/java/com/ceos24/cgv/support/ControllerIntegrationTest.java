package com.ceos24.cgv.support;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
public abstract class ControllerIntegrationTest {

    protected MockMvc mockMvc;

    @Autowired protected EntityManager em;

    @Autowired
    private WebApplicationContext wac;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    protected <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    protected void flushAndClear() {
        em.flush();
        em.clear();
    }
}
