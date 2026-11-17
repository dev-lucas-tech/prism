package com.prism.api;

import com.prism.transaction.SummaryDto;
import com.prism.transaction.TransactionDto;
import com.prism.transaction.TransactionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @GetMapping("/transactions")
    public List<TransactionDto> list() {
        return service.list();
    }

    @GetMapping("/transactions/{id}")
    public TransactionDto get(@PathVariable String id) {
        return service.getById(id);
    }

    @GetMapping("/summary")
    public SummaryDto summary() {
        return service.summary();
    }
}
