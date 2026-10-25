# Changelog

All notable changes to the **Beam Networks** Android app are listed here, newest first.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased] - 2026-10-25

### Added

- **PayHero and SMS balances on the dashboard**, as on the website: two cards under the Customers / Online / Tickets row. A balance below its alert level turns red with a "Low" tag. They are hidden for staff who can't see them, and the PayHero card is hidden when PayHero isn't your payment gateway.
- **Side menu.** A new menu button (top left) opens every part of the app in one place: Dashboard, Customers, Hotspot, Jobs, Tickets, Payments, Invoices, Income & expenses, Network status, Live sessions, Customer map, Announcements, SMS & email log, M-Pesa messages, Call log, Stock, Map, Settings and Sign out. Tickets and Stock show a count of what needs attention.
- **Hotspot.** Hotspot customers (suspend, reactivate, text their access code), vouchers (issue paid or free, resend, share the code) and live hotspot sessions (end a session).
- **Payments register.** Search and filter every payment. Confirm a pending M-Pesa payment, send the M-Pesa prompt again, and refund a payment (admins only, with a confirmation step).
- **Invoices register.** Filter by status, share the PDF, send to the customer, mark overdue, waive or cancel.
- **SMS & email log.** See what was sent, delivered, failed or not sent (with the reason), send a message again, and check delivery status.
- **Settings.** App alerts and caller ID for the phone, plus the business's billing settings: SMS and email on or off, alert phones with a test SMS, support phone and address, grace period, VAT, invoice prefix and footer, minimum pause, and outage detection.
- **Customer actions.** Extend service by days, weeks or until a date. Assign or release a static IP. Archive a customer.
- **Create customer form now matches the website.** Plan, network and status, a full location block (map pin, country, county, sub-county, ward, road, estate, street, building, house number, common marker, more details), the billed-account switch with a billing day, and referrals (with automatic matching of a pending referral lead by phone number).
- **Edit customer** now includes the full location and the billed-account switch.
- **Tickets:** four priorities (low, normal, high, urgent), queues, assigning on creation, escalating, and "Past SLA" warnings on the list and the ticket page.
- **Jobs:** a priority, and "what was done", a completion code and "customer confirmed" when marking a job done.
- **Stock:** item types (consumable, asset, tool), a Move button (issue to a technician, receive, return, install, write off), a movement history tab, archiving a product, and SKU, unit cost and selling price when adding one.
- **Invoices:** a quantity field when raising a one-off invoice.
- Signed release builds. A release APK is signed with a private key kept outside the repository.

### Changed

- The notification bell moved next to the account avatar (top right) to make room for the menu button.
- The Customers screen opens on the **Online** filter, which is now first in the list. The dashboard's Customers card still opens on All.
- Recording a payment with an M-Pesa code first checks Beam. A code that is already recorded is not recorded again, and an unmatched paybill deposit with that code is attached to the customer instead of being duplicated as cash.

### Fixed

- Low SMS and PayHero balance alerts never arrived, because the app couldn't read the balance figures Beam sends. They are read correctly now.
- A customer's **Recent payments** list was empty for anyone who had not paid this month. It now shows that customer's own payment history.
- The dashboard's "billed" total showed Ksh 0 when credit had been applied. It now counts what was billed before credit.
- Jobs created on the website (for example from a ticket) showed their description as the job's location. The text now appears as notes.
- Cancelled jobs disappeared from the Jobs screen. They now appear with the completed jobs, tagged Cancelled.
- Saving a customer's details could erase their location if the full record had not loaded. Location is now only sent once it has loaded.

## [1.0] - 2026-03-05

### Added

- Redesigned interface with a dashboard, bottom navigation, list and detail screens and bottom-sheet forms.
- Customers saved to the cloud and shown in the app for contacting, with open tickets on the dashboard.
- Ticket manager.

### Changed

- Gradle upgraded to 9.2.1 and Kotlin to 2.3.0 (October 2026) for Java 25 support.

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
