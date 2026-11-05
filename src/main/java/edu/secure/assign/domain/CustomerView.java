package edu.secure.assign.domain;

/** 응답 전용 DTO. 필요한 항목만, 마스킹해서 담는다. (비밀번호 해시는 아예 없음) */
public class CustomerView {

    private String customerId;
    private String customerName;
    private String rrnMasked;
    private String phoneMasked;

    public CustomerView() {
    }

    public CustomerView(String customerId, String customerName, String rrnMasked, String phoneMasked) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.rrnMasked = rrnMasked;
        this.phoneMasked = phoneMasked;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getRrnMasked() {
        return rrnMasked;
    }

    public void setRrnMasked(String rrnMasked) {
        this.rrnMasked = rrnMasked;
    }

    public String getPhoneMasked() {
        return phoneMasked;
    }

    public void setPhoneMasked(String phoneMasked) {
        this.phoneMasked = phoneMasked;
    }
}
