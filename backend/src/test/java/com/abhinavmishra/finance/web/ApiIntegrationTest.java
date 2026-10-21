package com.abhinavmishra.finance.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-end through the REST API against an in-memory H2 database in PostgreSQL mode. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:finance;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
})
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper mapper;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM merchant_overrides");
    }

    private static String sampleCsv() {
        StringBuilder sb = new StringBuilder("Date,Description,Amount\n");
        LocalDate start = LocalDate.of(2026, 1, 10);
        for (int m = 0; m < 6; m++) {
            LocalDate d = start.plusMonths(m);
            sb.append(d).append(",NETFLIX.COM 866-579-7172 CA,-15.49\n");
            sb.append(d.plusDays(1)).append(",ACME CORP PAYROLL,2000.00\n");
            sb.append(d.plusDays(2)).append(",SMITHS #4105 SANDY UT,-").append(60 + m * 7).append(".25\n");
            sb.append(d.plusDays(3)).append(",TRANSFER TO SAVINGS,-200.00\n");
            sb.append(d.plusDays(4)).append(",ODD LITTLE SHOP,-").append(10 + m).append(".00\n");
        }
        return sb.toString();
    }

    private JsonNode upload(String csv) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "bank.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        String body = mvc.perform(multipart("/api/transactions/import").file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    @Test
    void importIsIdempotent() throws Exception {
        JsonNode first = upload(sampleCsv());
        assertThat(first.get("inserted").asLong()).isEqualTo(30);
        JsonNode second = upload(sampleCsv());
        assertThat(second.get("inserted").asLong()).isZero();
        assertThat(second.get("duplicatesSkipped").asLong()).isEqualTo(30);
    }

    @Test
    void summaryExcludesTransfers() throws Exception {
        upload(sampleCsv());
        mvc.perform(get("/api/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionCount").value(30))
                .andExpect(jsonPath("$.income").value(12000.00))
                .andExpect(jsonPath("$.monthly.length()").value(6))
                .andExpect(jsonPath("$.spendingByCategory[*].category").value(hasItem("GROCERIES")));
    }

    @Test
    void recurringFindsSubscriptionButNotGroceries() throws Exception {
        upload(sampleCsv());
        mvc.perform(get("/api/analytics/recurring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].merchant").value("NETFLIX"))
                .andExpect(jsonPath("$.payments[0].subscription").value(true))
                .andExpect(jsonPath("$.monthlySubscriptionCost").value(15.49));
    }

    @Test
    void recategorizingAppliesToMerchantAndFutureImports() throws Exception {
        upload(sampleCsv());
        long id = jdbc.queryForObject("SELECT MIN(id) FROM transactions WHERE merchant = 'ODD LITTLE SHOP'", Long.class);

        mvc.perform(patch("/api/transactions/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"HEALTH\",\"applyToMerchant\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.category").value("HEALTH"))
                .andExpect(jsonPath("$.transaction.categorySource").value("MANUAL"))
                .andExpect(jsonPath("$.otherTransactionsUpdated").value(5));

        upload("Date,Description,Amount\n2026-08-01,ODD LITTLE SHOP,-9.00\n");
        mvc.perform(get("/api/transactions").param("q", "odd little").param("from", "2026-08-01"))
                .andExpect(jsonPath("$.items[0].category").value("HEALTH"))
                .andExpect(jsonPath("$.items[0].categorySource").value("MERCHANT_OVERRIDE"));
    }

    @Test
    void badCsvReturns422() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "bad.csv", "text/csv", "Foo,Bar\n1,2\n".getBytes());
        mvc.perform(multipart("/api/transactions/import").file(file))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unknownTransactionReturns404() throws Exception {
        mvc.perform(patch("/api/transactions/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"HEALTH\"}"))
                .andExpect(status().isNotFound());
    }
}
