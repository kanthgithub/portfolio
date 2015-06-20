package name.abuchen.portfolio.snapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import name.abuchen.portfolio.math.IRR;
import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.AccountTransaction.Type;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Transaction;
import name.abuchen.portfolio.money.CurrencyConverter;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.util.Interval;

public class ClientIRRYield
{
    public static ClientIRRYield create(Client client, ClientSnapshot snapshotStart, ClientSnapshot snapshotEnd)
    {
        Interval interval = Interval.of(snapshotStart.getTime(), snapshotEnd.getTime());

        List<Transaction> transactions = new ArrayList<Transaction>();
        collectAccountTransactions(client, interval, transactions);
        collectPortfolioTransactions(client, interval, transactions);
        Collections.sort(transactions, new Transaction.ByDate());

        List<Date> dates = new ArrayList<Date>();
        List<Double> values = new ArrayList<Double>();
        collectDatesAndValues(interval, snapshotStart, snapshotEnd, transactions, dates, values);

        double irr = IRR.calculate(dates, values);

        return new ClientIRRYield(irr);
    }

    private double irr;

    private ClientIRRYield(double irr)
    {
        this.irr = irr;
    }

    public double getIrr()
    {
        return irr;
    }

    private static void collectPortfolioTransactions(Client client, Interval interval, List<Transaction> transactions)
    {
        for (Portfolio portfolio : client.getPortfolios())
        {
            portfolio.getTransactions().stream() //
                            .filter(t -> interval.contains(t.getDate())) //
                            .forEach(t -> {
                                switch (t.getType())
                                {
                                    case TRANSFER_IN:
                                    case TRANSFER_OUT:
                                    case DELIVERY_INBOUND:
                                    case DELIVERY_OUTBOUND:
                                        transactions.add(t);
                                        break;
                                    case BUY:
                                    case SELL:
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                            });
        }
    }

    private static void collectAccountTransactions(Client client, Interval interval, List<Transaction> transactions)
    {
        for (Account account : client.getAccounts())
        {
            account.getTransactions().stream() //
                            .filter(t -> interval.contains(t.getDate())) //
                            .forEach(t -> {
                                switch (t.getType())
                                {
                                    case DEPOSIT:
                                    case REMOVAL:
                                    case TRANSFER_IN:
                                    case TRANSFER_OUT:
                                        transactions.add(t);
                                        break;
                                    case BUY:
                                    case SELL:
                                    case FEES:
                                    case TAXES:
                                    case DIVIDENDS:
                                    case INTEREST:
                                    case TAX_REFUND:
                                        break;
                                    default:
                                        throw new UnsupportedOperationException();
                                }
                            });
        }
    }

    private static void collectDatesAndValues(Interval interval, ClientSnapshot snapshotStart,
                    ClientSnapshot snapshotEnd, List<Transaction> transactions, List<Date> dates, List<Double> values)
    {
        CurrencyConverter converter = snapshotStart.getCurrencyConverter();

        Calendar cal = Calendar.getInstance();
        cal.setTime(Date.from(interval.getStart()));
        dates.add(cal.getTime());
        // snapshots are always in target currency, no conversion needed
        values.add(-snapshotStart.getMonetaryAssets().getAmount() / Values.Amount.divider());

        for (Transaction t : transactions)
        {
            cal.setTime(t.getDate());
            dates.add(cal.getTime());

            if (t instanceof AccountTransaction)
            {
                AccountTransaction at = (AccountTransaction) t;
                long amount = converter.convert(t.getDate(), t.getMonetaryAmount()).getAmount();
                if (at.getType() == Type.DEPOSIT || at.getType() == Type.TRANSFER_IN)
                    amount = -amount;
                values.add(amount / Values.Amount.divider());
            }
            else if (t instanceof PortfolioTransaction)
            {
                PortfolioTransaction pt = (PortfolioTransaction) t;
                long amount = converter.convert(t.getDate(), t.getMonetaryAmount()).getAmount();
                if (pt.getType() == PortfolioTransaction.Type.DELIVERY_INBOUND
                                || pt.getType() == PortfolioTransaction.Type.TRANSFER_IN)
                    amount = -amount;
                values.add(amount / Values.Amount.divider());
            }
            else
            {
                throw new UnsupportedOperationException();
            }
        }

        cal.setTime(Date.from(interval.getEnd()));
        dates.add(cal.getTime());
        values.add(snapshotEnd.getMonetaryAssets().getAmount() / Values.Amount.divider());
    }
}