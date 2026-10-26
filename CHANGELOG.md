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
- **Forgot password.** The sign-in screen has a "Forgot password?" link that works like the website: enter the phone number on your Beam account, get a 6-digit code by SMS, then enter it with a new password (at least 8 characters). You can ask for a new code after a minute, and the old code stops working. {2026-10-25 21:09}

### Changed

- **Notification bell moved.** The bell moved next to the account avatar (top right) to make room for the menu button. {2026-10-25 17:52}
- **In-app updates.** Beam OPS now updates itself without the Play Store. When you open the app it checks for a newer version and offers "Update now" or "Later"; it then downloads the new version, checks it is intact and opens Android's installer for you to confirm. Settings shows the app version, with a button to check for updates. The phone setup has a new "Install app updates" step, because Android needs your OK once before an app can install updates. Updates only install if they are signed with the same key as the app already on the phone. A new `scripts/publish-release.ps1` builds the signed app and publishes it for the updater, together with a download page for new installs: the version, a Download button, step-by-step install instructions and a QR code to open the page on a phone. The script writes its files in a format Vercel and the app can read, and stops with an error if the upload fails. It uploads to its own download site, separate from the changelog site. {2026-10-26 08:44}
- **Equipment questions on installation jobs.** Adding an installation now asks "Customer has a router" and the new "Customer has PoE" together, as two checkboxes. Both answers show on the job's page. {2026-10-26 06:24}
- **Technician is told about a new job.** When a technician is assigned to a job (new, or changed on an existing job), the app texts them the customer, place, time, phone number and whether the customer has a router and PoE. The text is sent from the phone that assigns the job, so it uses that phone's SMS rates and needs the new "Text technicians" permission, which is part of the phone setup. The app says whether the technician was texted, and if they have no phone number saved. The technician's own app also shows a "New installation assigned to you" notification and adds it to the in-app alerts; it can be switched off under App alerts as "Jobs assigned to you". {2026-10-26 06:24}
- **Phone setup on start-up.** When the app opens and a permission is missing, it walks you through one step at a time ("Step 2 of 6"), explaining why each is needed and what it is used for: notifications, phone and call log (caller ID and number picker), show over other apps (caller ID popup), battery, M-Pesa messages (SMS) and location. Each step can be skipped with "Not now". Steps you already answered are not asked again; Settings › This phone › Phone permissions reopens the whole walk-through and shows whether everything is on. {2026-10-26 06:14}
- **Battery setting opens directly.** The battery step takes you straight to Android's "Allow Beam OPS to run in the background" prompt (or the closest battery page on phones that lack it), and explains that without it alerts arrive late and caller ID may not appear. {2026-10-26 06:14}
- **Call log on Add customer.** The phone number field on the Add customer form now has a phone icon that opens the call log so you can pick a number. {2026-10-26 06:14}
- **Expenses from M-Pesa messages.** M-Pesa messages moved from the Account screen and side menu to Income & expenses. On the Expenses tab, "Log an expense from an M-Pesa message" opens your M-Pesa SMS; pick one and the expense form fills in the amount, who was paid (the shop or person) and the date. You then add the item, for example Ksh 5,000 paid to Faiba Optics for fibre cable. The expense form now has separate "Paid to" and "Item" fields, and the expenses list shows the shop under each item. Picking an M-Pesa message for a payment also fills in its M-Pesa reference. {2026-10-26 06:05}
- **Balances first in Settings.** The PayHero and SMS balance cards now sit at the very top of Settings, above "This phone". {2026-10-26 06:05}
- **Customers opens on Online.** The Customers screen opens on the **Online** filter, which sits right after All in the filter list. The dashboard's Customers card still opens on All. {2026-10-26 05:57}
- **Duplicate M-Pesa payments.** Recording a payment with an M-Pesa code first checks Beam. A code that is already recorded is not recorded again, and an unmatched paybill deposit with that code is attached to the customer instead of being duplicated as cash. {2026-10-25 17:52}
- **App renamed to Beam OPS.** The name under the icon on the phone, and the app's own messages (for example the notification permission prompts), now say Beam OPS. Messages sent to customers still use Beam Networks. {2026-10-25 20:27}
- **Bigger logo on the sign-in screen.** The logo is zoomed in so the mark fills its frame instead of floating in white space. {2026-10-25 20:41}
- **Bigger home-screen icon.** The Beam logo now fills the app icon instead of sitting small in a lot of white space, and adapts to round and squircle icon shapes. {2026-10-25 20:43}
- **Sign-in background.** The sign-in screen now has the same heavily blurred blue and orange network graphic as the Beam billing website sign-in page, scaled evenly to fit any screen size without stretching. The graphic itself stays still; six green dots travel along its lines from dot to dot, and each dot flashes green when one arrives. The green dots never leave the screen: where a line runs off the edge, the dot turns back at the edge. {2026-10-25 20:59}

### Fixed

- **Dashboard icon sometimes did nothing.** Tapping the first icon in the bottom bar did not always take you back to the dashboard. It now always returns to the dashboard, and tapping it while you are already there scrolls back to the top. {2026-10-26 06:14}
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
