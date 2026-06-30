import telebot
import os
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
import firebase_admin
from firebase_admin import credentials, firestore
from telebot.types import InlineKeyboardMarkup, InlineKeyboardButton

# Configuration
BOT_TOKEN = "8783064531:AAFzwboTsofBqw4NgCgt8YfwL83_KPMhF58"
ADMIN_USER_ID = 942862241

# Initialize Telegram Bot
bot = telebot.TeleBot(BOT_TOKEN)

# Initialize Firebase Admin SDK
db = None
try:
    cred = credentials.Certificate("serviceAccountKey.json")
    firebase_admin.initialize_app(cred)
    db = firestore.client()
    print("Firebase Admin initialized successfully.")
except Exception as e:
    print(f"Error initializing Firebase Admin (Make sure serviceAccountKey.json is in this folder): {e}")

# In-memory set to prevent spamming notifications for the same transaction in a session
notified_utrs = set()

# --- DUMMY WEB SERVER FOR RENDER/PYTHONANYWHERE ---
class DummyHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header('Content-type', 'text/plain')
        self.end_headers()
        self.wfile.write(b"SNALD Telegram Bot is running!")

def run_dummy_server():
    try:
        port = int(os.environ.get("PORT", 10000))
        server = HTTPServer(('0.0.0.0', port), DummyHandler)
        server.serve_forever()
    except OSError as e:
        print(f"Dummy web server failed to start (Port {port} might be in use): {e}")
# -----------------------------------

def send_approval_request(email, utr):
    try:
        markup = InlineKeyboardMarkup()
        markup.row(
            InlineKeyboardButton("Approve ✅", callback_data=f"approve:{utr}:{email}"),
            InlineKeyboardButton("Reject ❌", callback_data=f"reject:{utr}:{email}")
        )
        
        msg_text = (
            "🔔 **New PRO Activation Request!**\n\n"
            f"📧 **Email**: `{email}`\n"
            f"🎫 **UTR**: `{utr}`\n\n"
            "Please check your Paytm/UPI merchant history for Rs. 149/- matching this UTR."
        )
        
        bot.send_message(ADMIN_USER_ID, msg_text, parse_mode="Markdown", reply_markup=markup)
        print(f"Sent alert to admin for UTR {utr}")
    except Exception as e:
        print(f"Error sending Telegram message: {e}")

# Callback Handler for Inline Approve/Reject Buttons
@bot.callback_query_handler(func=lambda call: True)
def handle_approval_callback(call):
    if call.from_user.id != ADMIN_USER_ID:
        bot.answer_callback_query(call.id, text="Unauthorized.")
        return

    try:
        data = call.data.split(":")
        action = data[0]
        utr = data[1]
        email = data[2]

        if not db:
            bot.answer_callback_query(call.id, text="Database not connected.")
            return

        if action == "approve":
            # 1. Update user to isPro = True
            user_ref = db.collection("users").document(email)
            user_ref.set({"isPro": True}, merge=True)

            # 2. Update request status to APPROVED
            req_ref = db.collection("pro_requests").document(utr)
            req_ref.update({"status": "APPROVED"})

            bot.answer_callback_query(call.id, text=f"Approved user {email}!")
            bot.edit_message_text(
                chat_id=call.message.chat.id,
                message_id=call.message.message_id,
                text=f"✅ **APPROVED**\n\n📧 Email: `{email}`\n🎫 UTR: `{utr}`",
                parse_mode="Markdown"
            )
            print(f"Successfully approved Pro Mode for {email}")

        elif action == "reject":
            # Update request status to REJECTED
            req_ref = db.collection("pro_requests").document(utr)
            req_ref.update({"status": "REJECTED"})

            bot.answer_callback_query(call.id, text=f"Rejected UTR {utr}")
            bot.edit_message_text(
                chat_id=call.message.chat.id,
                message_id=call.message.message_id,
                text=f"❌ **REJECTED**\n\n📧 Email: `{email}`\n🎫 UTR: `{utr}`",
                parse_mode="Markdown"
            )
            print(f"Rejected UTR {utr}")

    except Exception as e:
        bot.answer_callback_query(call.id, text="Error processing action.")
        print(f"Callback error: {e}")

# Welcome Message for normal Telegram commands
@bot.message_handler(commands=['start', 'help'])
def send_welcome(message):
    welcome_text = (
        "Welcome to SNALD Pro support bot! 🎲\n\n"
        "To purchase PRO Mode:\n"
        "1. Open the SNALD app.\n"
        "2. Navigate to 'Go Pro' on the menu.\n"
        "3. Pay Rs. 149/- to UPI and submit your UTR directly inside the app.\n\n"
        "Once verified, your app will unlock instantly!"
    )
    bot.reply_to(message, welcome_text, parse_mode='Markdown')

# Setup Firestore Snapshot Listener
def listen_to_firestore():
    if not db:
        print("Firestore not available to listen.")
        return

    print("Starting Firestore listener for pending UTR requests...")
    query = db.collection("pro_requests").where("status", "==", "PENDING")

    def on_snapshot(col_snapshot, changes, read_time):
        for change in changes:
            if change.type.name == 'ADDED':
                doc = change.document
                data = doc.to_dict()
                utr = doc.id
                email = data.get("email")

                # Check to prevent spamming notifications in current session
                if utr not in notified_utrs:
                    notified_utrs.add(utr)
                    send_approval_request(email, utr)

    # Listen in real-time
    query.on_snapshot(on_snapshot)

if __name__ == "__main__":
    # Start the dummy web server in a background thread
    threading.Thread(target=run_dummy_server, daemon=True).start()

    # Start Firestore real-time listener
    if db:
        listen_to_firestore()

    # Start the Telegram bot polling loop
    print("SNALD Bot is running...")
    bot.infinity_polling()