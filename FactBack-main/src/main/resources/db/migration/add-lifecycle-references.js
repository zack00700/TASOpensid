db.getCollection('LIFECYCLE').find().forEach(lc => {
  db.getCollection('ITEM').updateOne(
    { _id: lc.itemId },
    { $addToSet: { lifeCycles: lc._id } }
  );
});
