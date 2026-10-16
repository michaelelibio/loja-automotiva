package com.garage.garageapi.order.recipient;

import com.garage.garageapi.address.entity.Address;
import com.garage.garageapi.order.dto.AliExpressRecipientRequest;
import com.garage.garageapi.order.entity.Order;
import com.garage.garageapi.product.entity.FulfillmentType;
import java.time.*;
import java.time.format.*;
import java.util.*;

public final class AliExpressRecipientData {
    private AliExpressRecipientData() { }
    private static final DateTimeFormatter BR_DATE=DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final Set<String> UFS=Set.of("AC","AL","AP","AM","BA","CE","DF","ES","GO","MA","MT","MS","MG","PA","PB","PR","PE","PI","RJ","RN","RS","RO","RR","SC","SP","SE","TO");
    private static final Set<String> DDDS=Set.of("11","12","13","14","15","16","17","18","19","21","22","24","27","28","31","32","33","34","35","37","38","41","42","43","44","45","46","47","48","49","51","53","54","55","61","62","63","64","65","66","67","68","69","71","73","74","75","77","79","81","82","83","84","85","86","87","88","89","91","92","93","94","95","96","97","98","99");
    public static Normalized validate(AliExpressRecipientRequest request,Address address) {
        String cpf=normalizeCpf(request==null ? null : request.cpf());
        String phone=normalizePhone(request==null ? null : request.phone());
        LocalDate birthDate=null;
        String raw=request==null ? null : request.birthDate();
        try { if(raw!=null && raw.matches("[0-9]{2}/[0-9]{2}/[0-9]{4}")) birthDate=LocalDate.parse(raw,BR_DATE); }
        catch(DateTimeParseException ignored) { /* Fixed validation messages only. */ }
        var errors=personalErrors(cpf,birthDate,phone);
        errors.putAll(addressErrors(address.getRecipientName(),address.getZipCode(),address.getStreet(),address.getNumber(),address.getNeighborhood(),address.getCity(),address.getState()));
        if(!errors.isEmpty()) throw new RecipientValidationException(errors);
        return new Normalized(cpf,birthDate,phone);
    }
    public static Status status(Order order) {
        boolean required=order.getItems().stream().anyMatch(i->i.getFulfillmentType()==FulfillmentType.DROPSHIPPING && "ALIEXPRESS".equalsIgnoreCase(i.getSupplier()));
        if(!required) return new Status(false,true,List.of());
        var errors=personalErrors(order.getRecipientCpf(),order.getRecipientBirthDate(),order.getRecipientPhone());
        errors.putAll(addressErrors(order.getRecipientName(),order.getZipCode(),order.getStreet(),order.getNumber(),order.getNeighborhood(),order.getCity(),order.getState()));
        return new Status(true,errors.isEmpty(),List.copyOf(errors.keySet()));
    }
    private static LinkedHashMap<String,String> personalErrors(String cpf,LocalDate birthDate,String phone) {
        var errors=new LinkedHashMap<String,String>();
        if(!cpfValid(cpf)) errors.put("recipient.cpf","Informe um CPF válido do destinatário.");
        if(!birthDateValid(birthDate)) errors.put("recipient.birthDate","Informe uma data de nascimento válida no formato DD/MM/AAAA.");
        if(!phoneValid(phone)) errors.put("recipient.phone","Informe um telefone válido com DDD.");
        return errors;
    }
    private static Map<String,String> addressErrors(String name,String zip,String street,String number,String neighborhood,String city,String state) {
        var errors=new LinkedHashMap<String,String>();
        if(name==null || name.trim().length()>150 || !name.trim().matches("[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*") || name.trim().split("\\s+").length<2)
            errors.put("shippingAddress.recipientName","Informe o nome completo do destinatário no endereço de entrega.");
        if(zip==null || !zip.matches("[0-9]{8}")) errors.put("shippingAddress.zipCode","Confira o CEP do endereço de entrega.");
        if(street==null || street.isBlank()) errors.put("shippingAddress.street","Informe a rua no endereço de entrega.");
        if(number==null || number.isBlank()) errors.put("shippingAddress.number","Informe o número no endereço de entrega.");
        if(neighborhood==null || neighborhood.isBlank()) errors.put("shippingAddress.neighborhood","Informe o bairro no endereço de entrega.");
        if(city==null || city.isBlank()) errors.put("shippingAddress.city","Informe a cidade no endereço de entrega.");
        if(state==null || !UFS.contains(state)) errors.put("shippingAddress.state","Confira a UF do endereço de entrega.");
        return errors;
    }
    private static String normalizeCpf(String raw) { return raw!=null && raw.trim().matches("(?:[0-9]{11}|[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2})") ? raw.replaceAll("[^0-9]","") : null; }
    private static String normalizePhone(String raw) {
        if(raw==null || !raw.matches("\\+?[0-9() -]{10,19}")) return null;
        String digits=raw.replaceAll("[^0-9]","");
        return (digits.length()==12 || digits.length()==13) && digits.startsWith("55") ? digits.substring(2) : digits;
    }
    public static boolean phoneValid(String phone) {
        return phone!=null && phone.matches("[0-9]{10,11}") && DDDS.contains(phone.substring(0,2)) && phone.substring(2).matches("(?:[2-5][0-9]{7}|9[0-9]{8})");
    }
    public static boolean birthDateValid(LocalDate date) {
        LocalDate today=LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        return date!=null && date.isBefore(today) && !date.isBefore(today.minusYears(120));
    }
    public static boolean cpfValid(String cpf) {
        if(cpf==null || !cpf.matches("[0-9]{11}") || cpf.chars().distinct().count()==1) return false;
        for(int size=9;size<=10;size++) {
            int sum=0;for(int i=0;i<size;i++) sum+=(cpf.charAt(i)-'0')*(size+1-i);
            int digit=(sum*10)%11;if(digit==10) digit=0;
            if(digit!=cpf.charAt(size)-'0') return false;
        }
        return true;
    }
    public record Normalized(String cpf,LocalDate birthDate,String phone) {
        @Override public String toString() { return "RecipientData[REDACTED]"; }
    }
    public record Status(boolean required,boolean complete,List<String> pendingFields) { }
}
