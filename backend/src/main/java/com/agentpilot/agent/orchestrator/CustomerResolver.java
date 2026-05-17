package com.agentpilot.agent.orchestrator;

import com.agentpilot.crm.entity.Customer;
import com.agentpilot.crm.service.CustomerService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class CustomerResolver {
    private final CustomerService customerService;

    public CustomerResolver(CustomerService customerService) {
        this.customerService = customerService;
    }

    public Customer resolve(Long customerId, String message, String tenantId, Long salesRepId) {
        if (customerId != null) {
            Customer customer = customerService.getById(customerId);
            return visibleTo(customer, tenantId, salesRepId) ? customer : null;
        }
        return customerService.findMentionedIn(message == null ? "" : message, tenantId, salesRepId).orElse(null);
    }

    public Customer resolve(Long requestCustomerId, String message, Map<String, Object> args, String tenantId, Long salesRepId) {
        Long argCustomerId = nullableLongArg(args, "customerId");
        if (argCustomerId != null) {
            Customer customer = customerService.getById(argCustomerId);
            if (visibleTo(customer, tenantId, salesRepId)) {
                return customer;
            }
        }
        String customerName = stringArg(args, "customerName", "");
        if (!customerName.isBlank()) {
            Optional<Customer> customer = customerService.findMentionedIn(customerName, tenantId, salesRepId);
            if (customer.isPresent()) {
                return customer.get();
            }
        }
        return resolve(requestCustomerId, message, tenantId, salesRepId);
    }

    public boolean visibleTo(Customer customer, String tenantId, Long salesRepId) {
        return customer != null
                && Objects.equals(customer.getTenantId(), tenantId)
                && Objects.equals(customer.getOwnerSalesRepId(), salesRepId);
    }

    private Long nullableLongArg(Map<String, Object> args, String key) {
        Object value = args == null ? null : args.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String stringArg(Map<String, Object> args, String key, String fallback) {
        Object value = args == null ? null : args.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() || "null".equalsIgnoreCase(text) ? fallback : text;
    }
}
