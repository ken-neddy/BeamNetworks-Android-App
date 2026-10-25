# Changelog

All notable changes to the **Beam Networks** Android app are listed here, newest first.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased] - 2026-10-25

### Added

- **PayHero and SMS balances in Settings.** Two cards at the top of Settings show the PayHero wallet and the SMS balance. A balance below its alert level turns red with a "Low" tag. They are hidden for staff who can't see them, and the PayHero card is hidden when PayHero isn't your payment gateway. When one is below its alert level, a red "Needs attention" row appears on the dashboard and opens Settings. {2026-10-25 19:39}
- **Side menu.** A new menu button (top left) opens every part of the app in one place: Dashboard, Customers, Hotspot, Jobs, Tickets, Payments, Invoices, Income & expenses, Network status, Live sessions, Customer map, Announcements, SMS & email log, M-Pesa messages, Call log, Stock, Map, Settings and Sign out. Tickets and Stock show a count of what needs attention. {2026-10-25 17:52}
- **Hotspot.** Hotspot customers (suspend, reactivate, text their access code), vouchers (issue paid or free, resend, share the code) and live hotspot sessions (end a session). The menu entry is just "Hotspot", and customers, vouchers and live sessions are its three tabs. {2026-10-25 20:39}
- **Payments register.** Search and filter every payment. Confirm a pending M-Pesa payment, send the M-Pesa prompt again, and refund a payment (admins only, with a confirmation step). {2026-10-25 17:52}
- **Invoices register.** Filter by status, share the PDF, send to the customer, mark overdue, waive or cancel. {2026-10-25 17:52}
- **SMS & email log.** See what was sent, delivered, failed or not sent (with the reason), send a message again, and check delivery status. {2026-10-25 17:52}
- **Settings.** App alerts and caller ID for the phone, plus the business's billing settings: SMS and email on or off, alert phones with a test SMS, support phone and address, grace period, VAT, invoice prefix and footer, minimum pause, and outage detection. {2026-10-25 17:52}
- **Customer actions.** Extend service by days, weeks or until a date. Assign or release a static IP. Archive a customer. {2026-10-25 17:52}
- **Create customer form now matches the website.** Plan, network and status, a full location block (map pin, country, county, sub-county, ward, road, estate, street, building, house number, common marker, more details), the billed-account switch with a billing day, and referrals (with automatic matching of a pending referral lead by phone number). {2026-10-25 17:52}
- **Edit customer** now includes the full location and the billed-account switch. {2026-10-25 17:52}
- **Tickets:** four priorities (low, normal, high, urgent), queues, assigning on creation, escalating, and "Past SLA" warnings on the list and the ticket page. {2026-10-25 17:52}
- **Jobs:** a priority, and "what was done", a completion code and "customer confirmed" when marking a job done. {2026-10-25 17:52}
- **Stock:** item types (consumable, asset, tool), a Move button (issue to a technician, receive, return, install, write off), a movement history tab, archiving a product, and SKU, unit cost and selling price when adding one. {2026-10-25 17:52}
- **Invoices:** a quantity field when raising a one-off invoice. {2026-10-25 17:52}
- **Signed release builds.** A release APK is signed with a private key kept outside the repository. {2026-10-25 07:19}
- **App rebuilt on the live Beam billing system.** Customers, payments, invoices, tickets, jobs and stock now come straight from billing.beamnetworks.co.ke instead of the old separate cloud copy, so the app and the website always agree. {2026-10-25 07:19}
- **Sign in with your Beam staff account.** The app stays signed in and renews its access on its own; sign out is in Settings and the side menu. {2026-10-25 07:19}
- **New look and navigation.** Redesigned dashboard, bottom navigation, list and detail screens, and bottom-sheet forms in the Beam Networks colours and logo. {2026-10-25 07:19}
- **Money tools.** Unmatched paybill receipts can be assigned to a customer, pending payments can be verified, and a payments report shows collections. {2026-10-25 07:19}
- **Network status.** Routers online or offline, outages, planned maintenance (create and cancel) and customer downtime, with a Compensate action that credits customers for time offline. {2026-10-25 07:19}
- **Live sessions and customer map.** See who is connected right now, and a map of customers by location. {2026-10-25 07:19}
- **Announcements.** Write and send an announcement to customers. {2026-10-25 07:19}
- **Alerts on the phone.** Notifications for routers going offline or back, outages and monitoring alerts, customer downtime, payments received, suspensions and reconnections, new tickets and SLA breaches, Beam account and low SMS or PayHero balances, and low stock. Each type can be switched on or off in App alerts. {2026-10-25 07:19}
- **Caller ID popup.** When a customer calls, the app shows who they are, with their account details. {2026-10-25 07:19}
- **Accounts, call log and M-Pesa messages.** Income and expenses (log an expense, edit it), the phone's call log with known customers highlighted, and M-Pesa messages that pre-fill a payment. {2026-10-25 07:19}
- **Public changelog page.** This changelog is published at beam-networks-changelog.vercel.app and rebuilt when the repository is pushed; each entry shows its title and the date and time it was made. {2026-10-25 20:15}

### Changed

- **Notification bell moved.** The bell moved next to the account avatar (top right) to make room for the menu button. {2026-10-25 17:52}
- **Customers opens on Online.** The Customers screen opens on the **Online** filter, which is now first in the list. The dashboard's Customers card still opens on All. {2026-10-25 17:52}
- **Duplicate M-Pesa payments.** Recording a payment with an M-Pesa code first checks Beam. A code that is already recorded is not recorded again, and an unmatched paybill deposit with that code is attached to the customer instead of being duplicated as cash. {2026-10-25 17:52}
- **App renamed to Beam OPS.** The name under the icon on the phone, and the app's own messages (for example the notification permission prompts), now say Beam OPS. Messages sent to customers still use Beam Networks. {2026-10-25 20:27}
- **Bigger logo on the sign-in screen.** The logo is zoomed in so the mark fills its frame instead of floating in white space. {2026-10-25 20:41}
- **Bigger home-screen icon.** The Beam logo now fills the app icon instead of sitting small in a lot of white space, and adapts to round and squircle icon shapes. {2026-10-25 20:43}

### Fixed

- **Low balance alerts.** They never arrived, because the app couldn't read the balance figures Beam sends. They are read correctly now. {2026-10-25 18:17}
- **Recent payments.** A customer's list was empty for anyone who had not paid this month. It now shows that customer's own payment history. {2026-10-25 17:52}
- **Billed total.** The dashboard's "billed" total showed Ksh 0 when credit had been applied. It now counts what was billed before credit. {2026-10-25 17:52}
- **Jobs from the website.** Jobs created on the website (for example from a ticket) showed their description as the job's location. The text now appears as notes. {2026-10-25 17:52}
- **Cancelled jobs.** Cancelled jobs disappeared from the Jobs screen. They now appear with the completed jobs, tagged Cancelled. {2026-10-25 17:52}
- **Saving a customer.** Saving a customer's details could erase their location if the full record had not loaded. Location is now only sent once it has loaded. {2026-10-25 17:52}

## [1.0] - 2026-03-05

### Added

- **Redesigned app.** Redesigned interface with a dashboard, bottom navigation, list and detail screens and bottom-sheet forms. {2026-03-05 19:42}
- **Customers in the cloud.** Customers saved to the cloud and shown in the app for contacting, with open tickets on the dashboard. {2026-03-05 19:42}
- **Ticket manager.** {2026-03-05 19:42}

### Changed

- **Gradle and Kotlin upgrade.** Gradle upgraded to 9.2.1 and Kotlin to 2.3.0 (October 2026) for Java 25 support. {2026-10-24 16:59}

## Earlier

### December 2025

- Add-product and available-stock screens, with a low-stock view on the dashboard and a per-product low-stock threshold.
- Call log highlights known clients.
- Map "my location" button updated.
- Raise-ticket screen shows all client names with name filtering.
- Complete-installation screen records the products used and subtracts them from stock.

### November 2025

- Monthly income view on the dashboard.
- Expenses page with filters; log an expense by reading payment messages and pre-filling the amount.
- Stock manager and ticket manager added; app restructured.
- Caller ID pop-up for incoming calls, redesigned to scale across screen sizes.
- Completed installations on the dashboard.
- Payhero Lipwa payment link inside the app.

### October 2025

- Call log screen, with phone numbers selectable into forms.
- First version of the Beam Networks operations app.
