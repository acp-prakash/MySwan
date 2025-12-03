// MongoDB Cleanup Script for etradePatternLookup Field
// Run this in MongoDB shell or Compass

// ========================================
// STEP 1: Check Current State
// ========================================

print("\n=== CURRENT STATE ===\n");
print("Total masters:", db.master.count());
print("With etradePatternLookup = true:", db.master.count({ etradePatternLookup: true }));
print("With etradePatternLookup = false:", db.master.count({ etradePatternLookup: false }));
print("Field missing:", db.master.count({ etradePatternLookup: { $exists: false } }));

print("\n=== Sample records WITHOUT field ===");
db.master.find(
  { etradePatternLookup: { $exists: false } },
  { ticker: 1, name: 1, etradePatternLookup: 1 }
).limit(5).forEach(printjson);

print("\n=== Check ELTP, HODU, CONX ===");
db.master.find(
  { ticker: { $in: ["ELTP", "HODU", "CONX"] } },
  { ticker: 1, etradePatternLookup: 1 }
).forEach(printjson);

// ========================================
// STEP 2: Set All Missing Fields to FALSE
// ========================================

print("\n=== UPDATING MISSING FIELDS TO FALSE ===\n");

var result = db.master.updateMany(
  { etradePatternLookup: { $exists: false } },
  { $set: { etradePatternLookup: false } }
);

print("Update result:");
printjson(result);
print("Records matched:", result.matchedCount);
print("Records modified:", result.modifiedCount);

// ========================================
// STEP 3: Verify Update
// ========================================

print("\n=== VERIFICATION AFTER UPDATE ===\n");
print("Total masters:", db.master.count());
print("With etradePatternLookup = true:", db.master.count({ etradePatternLookup: true }));
print("With etradePatternLookup = false:", db.master.count({ etradePatternLookup: false }));
print("Field missing:", db.master.count({ etradePatternLookup: { $exists: false } }));

if (db.master.count({ etradePatternLookup: { $exists: false } }) === 0) {
    print("\n✅ SUCCESS! All records now have etradePatternLookup field");
} else {
    print("\n❌ WARNING! Some records still missing field");
}

print("\n=== Check ELTP, HODU, CONX after update ===");
db.master.find(
  { ticker: { $in: ["ELTP", "HODU", "CONX"] } },
  { ticker: 1, etradePatternLookup: 1 }
).forEach(printjson);

// ========================================
// OPTIONAL: Enable Specific Tickers
// ========================================

print("\n=== To enable specific tickers, uncomment and modify: ===");
print("/*");
print("db.master.updateMany(");
print("  { ticker: { $in: ['AAPL', 'MSFT', 'GOOGL', 'AMZN', 'TSLA'] } },");
print("  { $set: { etradePatternLookup: true } }");
print(")");
print("*/");

print("\n=== DONE! ===");
print("Next steps:");
print("1. Restart Spring Boot application");
print("2. Fetch patterns from dashboard");
print("3. Check logs for 'Found X masters with eTrade pattern enabled'");
print("4. Verify ELTP, HODU, CONX are NOT in enabled list");

