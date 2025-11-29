package Pry_01.Web.de.Ventas.de.Computadoras.Dto;

public class MpPreferenceRequest {
    private String title;
    private String id;
    private Integer quantity;
    private Double unitPrice;

    public MpPreferenceRequest() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }
}
