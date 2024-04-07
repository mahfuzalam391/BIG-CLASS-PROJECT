package com.thelocalmarketplace.software;

import java.math.BigDecimal;

import com.thelocalmarketplace.hardware.BarcodedProduct;
import com.thelocalmarketplace.hardware.PLUCodedProduct;
import com.thelocalmarketplace.hardware.Product;
import com.thelocalmarketplace.software.communication.GUI.CustomerStationSoftware.CustomerStation;
import com.thelocalmarketplace.software.funds.Funds;
import com.thelocalmarketplace.software.funds.FundsObserver;
import com.thelocalmarketplace.software.funds.PaymentKind.Kind;
import com.thelocalmarketplace.software.product.Products;
import com.thelocalmarketplace.software.product.ProductsListener;

public class Coordination implements FundsObserver, ProductsListener {
	SelfCheckoutStationSoftware software;
    Funds funds;
    Products products;
    CustomerStation gui;

    public Coordination(SelfCheckoutStationSoftware software, Funds funds, Products products) {
        this.software = software;
        this.funds = funds;
        this.products = products;
    }
    
    public void setGUI(CustomerStation gui) {
    	this.gui = gui;
    }
    
    public void noValidChange() {
    	if(gui != null)
    		gui.handleRequestAssistance();
    }

    @Override
    public void fundsAdded(Funds fundsFacade, BigDecimal funds) {
    	if(gui != null)
    		gui.updatePayDisplay(funds.doubleValue());
    }

    @Override
    public void fundsRemoved(Funds fundsFacade, BigDecimal funds) {
    	if(gui != null)
    		gui.updatePayDisplay(-1*funds.doubleValue());
    }

    @Override
    public void fundsStored(Funds fundsFacade, BigDecimal funds) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'fundsStored'");
    }

    @Override
    public void fundsInvalid(Funds fundsFacade, Kind kind) {
    	if(gui != null)
    		gui.customerPopUp("The payment method was invalid.");
    }

    @Override
    public void fundsPaidInFull(Funds fundsFacade, BigDecimal changeReturned) {
    	if(gui != null)
    		gui.setPaymentSuccesful(changeReturned.doubleValue());
    }

    @Override
    public void fundsStationBlocked(Funds fundsFacade) {
    	if(gui != null)
    		gui.customerPopUp("Payment failed due to the station being blocked.");
    }
    
    @Override
    public void productAdded(Products productFacade, Product product) {
    	if(gui != null) {
    		gui.updatePayDisplay(0);
    	
	    	String name = "";
	    	
	    	if (product instanceof BarcodedProduct) {
	    		BarcodedProduct barcodedProduct = (BarcodedProduct) product;
	    		name = barcodedProduct.getDescription();
	    	} else if (product instanceof PLUCodedProduct) {
	    		PLUCodedProduct pluCodedProduct = (PLUCodedProduct) product;
	    		name = pluCodedProduct.getDescription();
	    	}
	    	gui.addProductToCart(name, product.getPrice());
    	}
    }
    
    @Override
    public void productRemoved(Products productFacade, Product product) {
    	if(gui != null)
    		gui.updatePayDisplay(0);
    }
    
    @Override
    public void productToBaggingArea(Products productFacade, Product product) {
    	if(gui != null)
    		gui.customerBaggingAreaPopUp(product);
    }

    @Override
    public void bagsPurchased(Products productFacade, long totalCost) {
    	if(gui != null)
    		gui.addProductToCart("Reusable Bag", totalCost);
    }
}
