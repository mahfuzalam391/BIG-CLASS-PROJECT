/**

 Name                      UCID

 Yotam Rojnov             30173949
 Duncan McKay             30177857
 Mahfuz Alam              30142265
 Luis Trigueros Granillo  30167989
 Lilia Skumatova          30187339
 Abdelrahman Abbas        30110374
 Talaal Irtija            30169780
 Alejandro Cardona        30178941
 Alexandre Duteau         30192082
 Grace Johnson            30149693
 Abil Momin               30154771
 Tara Ghasemi M. Rad      30171212
 Izabella Mawani          30179738
 Binish Khalid            30061367
 Fatima Khalid            30140757
 Lucas Kasdorf            30173922
 Emily Garcia-Volk        30140791
 Yuinikoru Futamata       30173228
 Joseph Tandyo            30182561
 Syed Haider              30143096
 Nami Marwah              30178528

 */

package com.thelocalmarketplace.software.funds;

import java.math.BigDecimal;

import java.util.Arrays;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import java.util.List;

import java.util.Map;

import java.util.Set;

import java.util.stream.Collectors;

import com.jjjwelectronics.EmptyDevice;

import com.jjjwelectronics.OverloadedDevice;
import com.jjjwelectronics.printer.IReceiptPrinter;
import com.tdc.CashOverloadException;

import com.tdc.DisabledException;

import com.tdc.NoCashAvailableException;
import com.tdc.Sink;
import com.tdc.banknote.BanknoteStorageUnit;
import com.tdc.banknote.IBanknoteDispenser;
import com.tdc.coin.Coin;
import com.tdc.coin.CoinStorageUnit;
import com.tdc.coin.ICoinDispenser;

import com.thelocalmarketplace.hardware.AbstractSelfCheckoutStation;
import com.thelocalmarketplace.software.SelfCheckoutStationSoftware;


/**
 * Funds facade class that handles all payments and change.
 */
public class Funds {
	protected BigDecimal totalPaid = BigDecimal.ZERO;
	protected Map<BigDecimal, Number> coinsAvailable;
	protected Map<BigDecimal, Number> banknotesAvailable;
	protected final SelfCheckoutStationSoftware checkoutStationSoftware;
	protected Set<FundsObserver> observers = new HashSet<>();

	/**
	 * Funds constructor which initializes all individual fund facades.
	 *
	 * @param checkoutStation The device facade that will be used to implement all low-level functions.
	 */
	public Funds(SelfCheckoutStationSoftware checkoutStation) {
		
		coinsAvailable = new HashMap<BigDecimal, Number>();
		banknotesAvailable = new HashMap<BigDecimal, Number>();
		
		if(checkoutStation == null)
			throw new IllegalArgumentException("The argument cannot be null");

		this.checkoutStationSoftware = checkoutStation;
		
		// register the coin payment handler to track coin available and that were entered into the checkout station
		CoinHandler coinHandler = new CoinHandler(this);
		checkoutStation.station.getCoinValidator().attach(coinHandler);
		Map<BigDecimal, ICoinDispenser> coinDispensersMap = this.checkoutStationSoftware.getStationHardware().getCoinDispensers();
		for( BigDecimal coin: coinDispensersMap.keySet()) {
			ICoinDispenser dispenser = coinDispensersMap.get(coin);
			dispenser.attach(coinHandler);
			coinsAvailable.put(coin, 0);
		}
		// register the banknote payment handler to track banknotes available and that were entered into the checkout station
		BanknoteHandler banknoteHandler = new BanknoteHandler(this);
		checkoutStation.station.getBanknoteValidator().attach(banknoteHandler);
		Map<BigDecimal, IBanknoteDispenser> banknoteDispensersMap = this.checkoutStationSoftware.getStationHardware().getBanknoteDispensers();

		for( BigDecimal banknote: banknoteDispensersMap.keySet()) {
			IBanknoteDispenser dispenser = banknoteDispensersMap.get(banknote);
			dispenser.attach(banknoteHandler);
			banknotesAvailable.put(banknote, 0);
		}

		CardHandler cardHandler = new CardHandler(this);
		checkoutStation.station.getCardReader().register(cardHandler);
	}

	/**
	 * Registers the given listener with this facade so that the listener will be
	 * notified of events emanating from here.
	 * 
	 * @param listener
	 *            The listener to be registered. No effect if it is already
	 *            registered. Cannot be null.
	 */
	public void register(FundsObserver listener) {
		observers.add(listener);
	}

	/**
	 * De-registers the given listener from this facade so that the listener will no
	 * longer be notified of events emanating from here.
	 * 
	 * @param listener
	 *            The listener to be de-registered. No effect if it is not already
	 *            registered or null.
	 */
	public void deregister(FundsObserver listener) {
		observers.remove(listener);
	}

	/**
	 * Notifies observers that the payment funds are invalid for the specified payment kind.
	 * 
	 * @param kind The kind of payment for which the funds are invalid.
	 */
	protected void notifyInvalidFunds(PaymentKind.Kind kind) {
		for(FundsObserver observer : observers)
			observer.fundsInvalid(this, kind);
	}
	
	/**
	 * Notifies observers that the payment funds have been paid in full, along with the change returned.
	 * 
	 * @param changeReturned The amount of change returned to the customer.
	 */
	protected void notifyPaidFunds(BigDecimal changeReturned) {
		for(FundsObserver observer : observers)
			observer.fundsPaidInFull(this, changeReturned);
	}
	
	/**
	 * Notifies observers that the payment funds have been added, along with the amount added.
	 * 
	 * @param amount The amount of funds added by the customer.
	 */
	protected void notifyFundsAdded(BigDecimal amount) {
		for(FundsObserver observer : observers)
			observer.fundsAdded(this, amount);
	}
	
	/**
	 * Notifies observers that the payment funds have removed, along with the amount removed.
	 * 
	 * @param amount The amount of funds removed by the customer.
	 */
	protected void notifyFundsRemoved(BigDecimal amount) {
		for(FundsObserver observer : observers)
			observer.fundsRemoved(this, amount);
	}

	/**
	 * Notifies observers that the station is blocked at payment state.
	 */
	protected void notifyFundsStationBlocked() {
		for (FundsObserver observer : observers)
			observer.fundsStationBlocked(this, true);
	}
	
	protected void notifyCoinsHigh(CoinStorageUnit storage) {
		for (FundsObserver observer : observers)
			observer.highCoinsError(storage);
	}
	
	protected void notifyCoinsLow(ICoinDispenser dispenser) {
		for (FundsObserver observer : observers)
			observer.lowCoinsError(dispenser);
	}
	
	protected void notifyBanknotesHigh(BanknoteStorageUnit storage) {
		for (FundsObserver observer : observers)
			observer.highBanknotesError(storage);
	}
	
	protected void notifyBanknotesLow(IBanknoteDispenser dispenser) {
		for (FundsObserver observer : observers)
			observer.lowBanknotesError(dispenser);
	}
	

	public BigDecimal getTotalPaid() {
		return totalPaid;
	}

	public BigDecimal addToTotalPaid(BigDecimal amountPaid) {
		totalPaid = totalPaid.add(amountPaid);
		return totalPaid;
	}

	public BigDecimal getMoneyLeft() {
		BigDecimal total = new BigDecimal(checkoutStationSoftware.getTotalOrderPrice());
		return total.subtract(getTotalPaid());
	}
	
	/**
	 * Dispenses the correct amount of change to the customer and gives them the
	 * choice to print a receipt.
	 *
	 * Implements change dispensing logic using available coin denominations.
	 *
	 * @param changeValue The amount of change to be dispensed.
	 * @return true if correct change is dispensed, false otherwise.
	 * @throws DisabledException        If the coin slot is disabled.
	 * @throws CashOverloadException    If the cash storage is overloaded.
	 * @throws NoCashAvailableException If no cash is available for dispensing
	 *                                  change.
	 * @throws OverloadedDevice
	 * @throws EmptyDevice
	 */
	public boolean dispenseAccurateChange(BigDecimal changeValue) throws CashOverloadException, NoCashAvailableException, DisabledException{
		AbstractSelfCheckoutStation station = (AbstractSelfCheckoutStation) checkoutStationSoftware.getStationHardware();
		
		BigDecimal amountDispensed = new BigDecimal("0.0");
		BigDecimal remainingAmount = changeValue;
		List<BigDecimal> coinDenominations = station.getCoinDenominations();
		Collections.sort(coinDenominations);
		Collections.reverse(coinDenominations);
		List<BigDecimal> bankNoteDenominations = Arrays.stream(station.getBanknoteDenominations())
				.collect(Collectors.toList());
		Collections.sort(bankNoteDenominations);
		Collections.reverse(bankNoteDenominations);

		// This approach aims to find the optimal combination of denominations to minimize the
		// number of banknotes and coins used while considering the limited availability of
		// each denomination.
		while (remainingAmount.compareTo(BigDecimal.ZERO) > 0) {
			// If neither banknotes nor coins can be used, break the loop
			BigDecimal lowestCoin = coinDenominations.get(coinDenominations.size() - 1);
			BigDecimal lowestBankNote = bankNoteDenominations.get(bankNoteDenominations.size() - 1);
			BigDecimal lowestVal;
			int sizeOfLowest;
			if(lowestCoin.compareTo(lowestBankNote) > 0) {
				lowestVal = lowestBankNote;
				sizeOfLowest = (int)banknotesAvailable.get(lowestVal);
			}
			else {
				lowestVal = lowestCoin;
				sizeOfLowest = (int)coinsAvailable.get(lowestVal);
			}
			if (remainingAmount.compareTo(lowestVal) < 0 && ( sizeOfLowest > 0) ) {
				station.getCoinDispensers().get(lowestVal).emit();
				amountDispensed = changeValue;
				remainingAmount = BigDecimal.ZERO;
				break;
			}
			
			boolean dispensed = false;
			// Try using banknotes first
			for (BigDecimal bankNote : bankNoteDenominations) {
				if (remainingAmount.compareTo(bankNote) >= 0 && (int)banknotesAvailable.get(bankNote) > 0) {
					station.getBanknoteDispensers().get(bankNote).emit();
					amountDispensed = amountDispensed.add(bankNote);
					remainingAmount = remainingAmount.subtract(bankNote);
					dispensed = true;
					break;
				}
			}

			// If no banknotes are available or insufficient, try using coins
			if (!dispensed) {
				for (BigDecimal coin : coinDenominations) {
					if (remainingAmount.compareTo(coin) >= 0 && (int)coinsAvailable.get(coin) > 0) {
						station.getCoinDispensers().get(coin).emit();
						amountDispensed = amountDispensed.add(coin);
						remainingAmount = remainingAmount.subtract(coin);
						dispensed = true;
						break;
					}
				}
			}
			if(!dispensed)
				break;
		}

		return remainingAmount.compareTo(BigDecimal.ZERO) == 0;
	}
	
	
}