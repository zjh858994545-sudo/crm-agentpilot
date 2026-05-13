package com.agentpilot.crm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("crm_customer")
public class Customer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String industry;
    private String city;
    private String address;
    private String contactName;
    private String contactMobile;
    private String lifecycleStage;
    private String valueLevel;
    private String riskLevel;
    private Long ownerSalesRepId;
    private LocalDateTime lastContactAt;
    private LocalDateTime nextFollowTime;
    private LocalDateTime packageExpireAt;
    private String tags;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactMobile() {
        return contactMobile;
    }

    public void setContactMobile(String contactMobile) {
        this.contactMobile = contactMobile;
    }

    public String getLifecycleStage() {
        return lifecycleStage;
    }

    public void setLifecycleStage(String lifecycleStage) {
        this.lifecycleStage = lifecycleStage;
    }

    public String getValueLevel() {
        return valueLevel;
    }

    public void setValueLevel(String valueLevel) {
        this.valueLevel = valueLevel;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Long getOwnerSalesRepId() {
        return ownerSalesRepId;
    }

    public void setOwnerSalesRepId(Long ownerSalesRepId) {
        this.ownerSalesRepId = ownerSalesRepId;
    }

    public LocalDateTime getLastContactAt() {
        return lastContactAt;
    }

    public void setLastContactAt(LocalDateTime lastContactAt) {
        this.lastContactAt = lastContactAt;
    }

    public LocalDateTime getNextFollowTime() {
        return nextFollowTime;
    }

    public void setNextFollowTime(LocalDateTime nextFollowTime) {
        this.nextFollowTime = nextFollowTime;
    }

    public LocalDateTime getPackageExpireAt() {
        return packageExpireAt;
    }

    public void setPackageExpireAt(LocalDateTime packageExpireAt) {
        this.packageExpireAt = packageExpireAt;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

