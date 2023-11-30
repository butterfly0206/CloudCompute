package com.autodesk.compute.discovery;

import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.models.stream.ClaimedMessages;
import io.lettuce.core.models.stream.PendingMessage;
import io.lettuce.core.models.stream.PendingMessages;
import io.lettuce.core.output.*;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.CommandType;
import io.lettuce.core.protocol.ProtocolKeyword;
import io.lettuce.core.protocol.RedisCommand;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@SuppressWarnings("deprecation")
public class FakeRedisCommands implements
        RedisCommands<String, String> {
    private final HashMap<String, String> cache;

    public FakeRedisCommands() {
        cache = new HashMap<>();
    }

    @Override
    public Set<AclCategory> aclCat() {
        return null;
    }

    @Override
    public Set<CommandType> aclCat(final AclCategory category) {
        return null;
    }

    @Override
    public Long aclDeluser(final String... usernames) {
        return null;
    }

    @Override
    public String aclDryRun(final String s, final String s1, final String... strings) {
        return null;
    }

    @Override
    public String aclDryRun(final String s, final RedisCommand<String, String, ?> redisCommand) {
        return null;
    }

    @Override
    public String aclGenpass() {
        return null;
    }

    @Override
    public String aclGenpass(final int bits) {
        return null;
    }

    @Override
    public List<Object> aclGetuser(final String username) {
        return null;
    }

    @Override
    public List<String> aclList() {
        return null;
    }

    @Override
    public String aclLoad() {
        return null;
    }

    @Override
    public List<Map<String, Object>> aclLog() {
        return null;
    }

    @Override
    public List<Map<String, Object>> aclLog(final int count) {
        return null;
    }

    @Override
    public String aclLogReset() {
        return null;
    }

    @Override
    public String aclSave() {
        return null;
    }

    @Override
    public String aclSetuser(final String username, final AclSetuserArgs setuserArgs) {
        return null;
    }

    @Override
    public List<String> aclUsers() {
        return null;
    }

    @Override
    public String aclWhoami() {
        return null;
    }

    @Override
    public Long append(final String key, final String value) {
        return null;
    }

    @Override
    public String auth(final CharSequence charSequence) {
        return null;
    }

    @Override
    public String auth(final String password, final CharSequence charSequence) {
        return null;
    }

    @Override
    public Long bitcount(final String key) {
        return null;
    }

    @Override
    public Long bitcount(final String key, final long start, final long end) {
        return null;
    }

    @Override
    public List<Long> bitfield(final String key, final BitFieldArgs bitFieldArgs) {
        return null;
    }

    @Override
    public Long bitpos(final String key, final boolean state) {
        return null;
    }

    @Override
    public Long bitpos(final String key, final boolean state, final long start) {
        return null;
    }

    @Override
    public Long bitpos(final String key, final boolean state, final long start, final long end) {
        return null;
    }

    @Override
    public Long bitopAnd(final String destination, final String... keys) {
        return null;
    }

    @Override
    public Long bitopNot(final String destination, final String source) {
        return null;
    }

    @Override
    public Long bitopOr(final String destination, final String... keys) {
        return null;
    }

    @Override
    public Long bitopXor(final String destination, final String... keys) {
        return null;
    }

    @Override
    public Long decr(final String key) {
        return null;
    }

    @Override
    public Long decrby(final String key, final long amount) {
        return null;
    }

    @Override
    public String get(final String key) {
        return cache.get(key);
    }

    @Override
    public Long getbit(final String key, final long offset) {
        return null;
    }

    @Override
    public String getdel(final String key) {
        return null;
    }

    @Override
    public String getex(final String key, final GetExArgs args) {
        return null;
    }

    @Override
    public String getrange(final String key, final long start, final long end) {
        return null;
    }

    @Override
    public String getset(final String key, final String value) {
        return null;
    }

    @Override
    public Long incr(final String key) {
        return null;
    }

    @Override
    public Long incrby(final String key, final long amount) {
        return null;
    }

    @Override
    public Double incrbyfloat(final String key, final double amount) {
        return null;
    }

    @Override
    public List<KeyValue<String, String>> mget(final String... keys) {
        return null;
    }

    @Override
    public Long mget(final KeyValueStreamingChannel<String, String> channel, final String... keys) {
        return null;
    }

    @Override
    public String mset(final Map<String, String> map) {
        return null;
    }

    @Override
    public Boolean msetnx(final Map<String, String> map) {
        return null;
    }

    @Override
    public String set(final String key, final String value) {
        return null;
    }

    @Override
    public String set(final String key, final String value, final SetArgs setArgs) {
        cache.put(key, value);
        return "";
    }

    @Override
    public String setGet(final String key, final String value) {
        return null;
    }

    @Override
    public String setGet(final String key, final String value, final SetArgs setArgs) {
        return null;
    }

    @Override
    public Long setbit(final String key, final long offset, final int value) {
        return null;
    }

    @Override
    public String setex(final String key, final long seconds, final String value) {
        return null;
    }

    @Override
    public String psetex(final String key, final long milliseconds, final String value) {
        return null;
    }

    @Override
    public Boolean setnx(final String key, final String value) {
        return null;
    }

    @Override
    public Long setrange(final String key, final long offset, final String value) {
        return null;
    }

    @Override
    public StringMatchResult stralgoLcs(final StrAlgoArgs strAlgoArgs) {
        return null;
    }

    @Override
    public Long strlen(final String key) {
        return null;
    }

    @Override
    public Boolean copy(final String source, final String destination) {
        return null;
    }

    @Override
    public Boolean copy(final String source, final String destination, final CopyArgs copyArgs) {
        return null;
    }

    @Override
    public Long del(final String... keys) {
        return null;
    }

    @Override
    public Long unlink(final String... keys) {
        return null;
    }

    @Override
    public byte[] dump(final String key) {
        return new byte[0];
    }

    @Override
    public Long exists(final String... keys) {
        return null;
    }

    @Override
    public Boolean expire(final String key, final long seconds) {
        if (seconds == 0) {
            cache.remove(key);
            return true;
        }
        return false;
    }

    @Override
    public Boolean expire(final String s, final long l, final ExpireArgs expireArgs) {
        return null;
    }

    @Override
    public Boolean expire(final String key, final Duration seconds) {
        return null;
    }

    @Override
    public Boolean expire(final String s, final Duration duration, final ExpireArgs expireArgs) {
        return null;
    }

    @Override
    public Boolean expireat(final String key, final Date timestamp) {
        return null;
    }

    @Override
    public Boolean expireat(final String s, final Date date, final ExpireArgs expireArgs) {
        return null;
    }

    @Override
    public Boolean expireat(final String key, final Instant timestamp) {
        return null;
    }

    @Override
    public Boolean expireat(final String s, final Instant instant, final ExpireArgs expireArgs) {
        return null;
    }

    @Override
    public Long expiretime(final String s) {
        return null;
    }

    @Override
    public Boolean expireat(final String key, final long timestamp) {
        return null;
    }

    @Override
    public Boolean expireat(final String s, final long l, final ExpireArgs expireArgs) {
        return null;
    }

    @Override
    public List<String> keys(final String pattern) {
        return null;
    }

    @Override
    public Long keys(final KeyStreamingChannel<String> channel, final String pattern) {
        return null;
    }

    @Override
    public String migrate(final String host, final int port, final String key, final int db, final long timeout) {
        return null;
    }

    @Override
    public String migrate(final String host, final int port, final int db, final long timeout, final MigrateArgs<String> migrateArgs) {
        return null;
    }

    @Override
    public Boolean move(final String key, final int db) {
        return null;
    }

    @Override
    public String objectEncoding(final String key) {
        return null;
    }

    @Override
    public Long objectFreq(final String key) {
        return null;
    }

    @Override
    public Long objectIdletime(final String key) {
        return null;
    }

    @Override
    public Long objectRefcount(final String key) {
        return null;
    }

    @Override
    public Boolean persist(final String key) {
        return null;
    }

    @Override
    public Boolean pexpire(final String key, final long milliseconds) {
        return null;
    }

    @Override
    public Boolean pexpire(final String s, final long l, final ExpireArgs expireArgs) {
        return null;
    }

    @Override
    public Boolean pexpire(final String key, final Duration milliseconds) {
        return null;
    }

    @Override
    public Boolean pexpire(final String s, final Duration duration, final ExpireArgs expireArgs) {
        return null;
    }

    @Override
    public Boolean pexpireat(final String key, final Date timestamp) {
        return null;
    }

    @Override
    public Boolean pexpireat(final String s, final Date date, final ExpireArgs expireArgs) {
        return null;
    }

    @Override
    public Boolean pexpireat(final String key, final Instant timestamp) {
        return null;
    }

    @Override
    public Boolean pexpireat(final String s, final Instant instant, final ExpireArgs expireArgs) {
        return null;
    }

    @Override
    public Long pexpiretime(final String s) {
        return null;
    }

    @Override
    public Boolean pexpireat(final String key, final long timestamp) {
        return null;
    }

    @Override
    public Boolean pexpireat(final String s, final long l, final ExpireArgs expireArgs) {
        return null;
    }

    @Override
    public Long pttl(final String key) {
        return null;
    }

    @Override
    public String randomkey() {
        return null;
    }

    @Override
    public String rename(final String key, final String newKey) {
        return null;
    }

    @Override
    public Boolean renamenx(final String key, final String newKey) {
        return null;
    }

    @Override
    public String restore(final String key, final long ttl, final byte[] value) {
        return null;
    }

    @Override
    public String restore(final String key, final byte[] value, final RestoreArgs args) {
        return null;
    }

    @Override
    public List<String> sort(final String key) {
        return null;
    }

    @Override
    public Long sort(final ValueStreamingChannel<String> channel, final String key) {
        return null;
    }

    @Override
    public List<String> sort(final String key, final SortArgs sortArgs) {
        return null;
    }

    @Override
    public Long sort(final ValueStreamingChannel<String> channel, final String key, final SortArgs sortArgs) {
        return null;
    }

    @Override
    public List<String> sortReadOnly(final String s) {
        return null;
    }

    @Override
    public Long sortReadOnly(final ValueStreamingChannel<String> valueStreamingChannel, final String s) {
        return null;
    }

    @Override
    public List<String> sortReadOnly(final String s, final SortArgs sortArgs) {
        return null;
    }

    @Override
    public Long sortReadOnly(final ValueStreamingChannel<String> valueStreamingChannel, final String s, final SortArgs sortArgs) {
        return null;
    }

    @Override
    public Long sortStore(final String key, final SortArgs sortArgs, final String destination) {
        return null;
    }

    @Override
    public Long touch(final String... keys) {
        return null;
    }

    @Override
    public Long ttl(final String key) {
        return null;
    }

    @Override
    public String type(final String key) {
        return null;
    }

    @Override
    public KeyScanCursor<String> scan() {
        return null;
    }

    @Override
    public KeyScanCursor<String> scan(final ScanArgs scanArgs) {
        return null;
    }

    @Override
    public KeyScanCursor<String> scan(final ScanCursor scanCursor, final ScanArgs scanArgs) {
        return null;
    }

    @Override
    public KeyScanCursor<String> scan(final ScanCursor scanCursor) {
        return null;
    }

    @Override
    public StreamScanCursor scan(final KeyStreamingChannel<String> channel) {
        return null;
    }

    @Override
    public StreamScanCursor scan(final KeyStreamingChannel<String> channel, final ScanArgs scanArgs) {
        return null;
    }

    @Override
    public StreamScanCursor scan(final KeyStreamingChannel<String> channel, final ScanCursor scanCursor, final ScanArgs scanArgs) {
        return null;
    }

    @Override
    public StreamScanCursor scan(final KeyStreamingChannel<String> channel, final ScanCursor scanCursor) {
        return null;
    }

    @Override
    public void setTimeout(final Duration timeout) {

    }
    
    @Override
    public String clusterBumpepoch() {
        return null;
    }

    @Override
    public String clusterMeet(final String ip, final int port) {
        return null;
    }

    @Override
    public String clusterForget(final String nodeId) {
        return null;
    }

    @Override
    public String clusterAddSlots(final int... slots) {
        return null;
    }

    @Override
    public String clusterDelSlots(final int... slots) {
        return null;
    }

    @Override
    public String clusterDelSlotsRange(final Range<Integer>... ranges) {
        return null;
    }

    @Override
    public String clusterSetSlotNode(final int slot, final String nodeId) {
        return null;
    }

    @Override
    public String clusterSetSlotStable(final int slot) {
        return null;
    }

    @Override
    public List<Object> clusterShards() {
        return null;
    }

    @Override
    public String clusterSetSlotMigrating(final int slot, final String nodeId) {
        return null;
    }

    @Override
    public String clusterSetSlotImporting(final int slot, final String nodeId) {
        return null;
    }

    @Override
    public String clusterInfo() {
        return null;
    }

    @Override
    public String clusterMyId() {
        return null;
    }

    @Override
    public String clusterNodes() {
        return null;
    }

    @Override
    public List<String> clusterSlaves(final String nodeId) {
        return null;
    }

    @Override
    public List<String> clusterGetKeysInSlot(final int slot, final int count) {
        return null;
    }

    @Override
    public Long clusterCountKeysInSlot(final int slot) {
        return null;
    }

    @Override
    public Long clusterCountFailureReports(final String nodeId) {
        return null;
    }

    @Override
    public Long clusterKeyslot(final String key) {
        return null;
    }

    @Override
    public String clusterSaveconfig() {
        return null;
    }

    @Override
    public String clusterSetConfigEpoch(final long configEpoch) {
        return null;
    }

    @Override
    public List<Object> clusterSlots() {
        return null;
    }

    @Override
    public String asking() {
        return null;
    }

    @Override
    public String clusterReplicate(final String nodeId) {
        return null;
    }

    @Override
    public List<String> clusterReplicas(final String nodeId) {
        return null;
    }

    @Override
    public String clusterFailover(final boolean force) {
        return null;
    }

    @Override
    public String clusterReset(final boolean hard) {
        return null;
    }

    @Override
    public String clusterFlushslots() {
        return null;
    }

    @Override
    public String clusterAddSlotsRange(final Range<Integer>... ranges) {
        return null;
    }

    @Override
    public String select(final int db) {
        return null;
    }

    @Override
    public String swapdb(final int db1, final int db2) {
        return null;
    }

    @Override
    public StatefulRedisConnection<String, String> getStatefulConnection() {
        return null;
    }

    @Override
    public Long publish(final String channel, final String message) {
        return null;
    }

    @Override
    public List<String> pubsubChannels() {
        return null;
    }

    @Override
    public List<String> pubsubChannels(final String channel) {
        return null;
    }

    @Override
    public Map<String, Long> pubsubNumsub(final String... channels) {
        return null;
    }

    @Override
    public Long pubsubNumpat() {
        return null;
    }

    @Override
    public String echo(final String msg) {
        return null;
    }

    @Override
    public List<Object> role() {
        return null;
    }

    @Override
    public String ping() {
        return null;
    }

    @Override
    public String readOnly() {
        return null;
    }

    @Override
    public String readWrite() {
        return null;
    }

    @Override
    public String quit() {
        return null;
    }

    @Override
    public Long waitForReplication(final int replicas, final long timeout) {
        return null;
    }

    @Override
    public <T> T dispatch(final ProtocolKeyword type, final CommandOutput<String, String, T> output) {
        return null;
    }

    @Override
    public <T> T dispatch(final ProtocolKeyword type, final CommandOutput<String, String, T> output, final CommandArgs<String, String> args) {
        return null;
    }

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public void reset() {

    }

    @Override
    public Long geoadd(final String key, final double longitude, final double latitude, final String member) {
        return null;
    }

    @Override
    public Long geoadd(final String key, final double longitude, final double latitude, final String member, final GeoAddArgs args) {
        return null;
    }

    @Override
    public Long geoadd(final String key, final Object... lngLatMember) {
        return null;
    }

    @Override
    public Long geoadd(final String key, final GeoValue<String>... values) {
        return null;
    }

    @Override
    public Long geoadd(final String key, final GeoAddArgs args, final Object... lngLatMember) {
        return null;
    }

    @Override
    public Long geoadd(final String key, final GeoAddArgs args, final GeoValue<String>... values) {
        return null;
    }

    @Override
    public List<Value<String>> geohash(final String key, final String... members) {
        return null;
    }

    @Override
    public Set<String> georadius(final String key, final double longitude, final double latitude, final double distance, final GeoArgs.Unit unit) {
        return null;
    }

    @Override
    public List<GeoWithin<String>> georadius(final String key, final double longitude, final double latitude, final double distance, final GeoArgs.Unit unit, final GeoArgs geoArgs) {
        return null;
    }

    @Override
    public Long georadius(final String key, final double longitude, final double latitude, final double distance, final GeoArgs.Unit unit, final GeoRadiusStoreArgs<String> geoRadiusStoreArgs) {
        return null;
    }

    @Override
    public Set<String> georadiusbymember(final String key, final String member, final double distance, final GeoArgs.Unit unit) {
        return null;
    }

    @Override
    public List<GeoWithin<String>> georadiusbymember(final String key, final String member, final double distance, final GeoArgs.Unit unit, final GeoArgs geoArgs) {
        return null;
    }

    @Override
    public Long georadiusbymember(final String key, final String member, final double distance, final GeoArgs.Unit unit, final GeoRadiusStoreArgs<String> geoRadiusStoreArgs) {
        return null;
    }

    @Override
    public Set<String> geosearch(final String key, final GeoSearch.GeoRef<String> reference, final GeoSearch.GeoPredicate predicate) {
        return null;
    }

    @Override
    public List<GeoWithin<String>> geosearch(final String key, final GeoSearch.GeoRef<String> reference, final GeoSearch.GeoPredicate predicate, final GeoArgs geoArgs) {
        return null;
    }

    @Override
    public Long geosearchstore(final String destination, final String key, final GeoSearch.GeoRef<String> reference, final GeoSearch.GeoPredicate predicate, final GeoArgs geoArgs, final boolean storeDist) {
        return null;
    }

    @Override
    public List<GeoCoordinates> geopos(final String key, final String... members) {
        return null;
    }

    @Override
    public Double geodist(final String key, final String from, final String to, final GeoArgs.Unit unit) {
        return null;
    }

    @Override
    public Long pfadd(final String key, final String... values) {
        return null;
    }

    @Override
    public String pfmerge(final String destkey, final String... sourcekeys) {
        return null;
    }

    @Override
    public Long pfcount(final String... keys) {
        return null;
    }

    @Override
    public Long hdel(final String key, final String... fields) {
        return null;
    }

    @Override
    public Boolean hexists(final String key, final String field) {
        return null;
    }

    @Override
    public String hget(final String key, final String field) {
        return null;
    }

    @Override
    public Long hincrby(final String key, final String field, final long amount) {
        return null;
    }

    @Override
    public Double hincrbyfloat(final String key, final String field, final double amount) {
        return null;
    }

    @Override
    public Map<String, String> hgetall(final String key) {
        return null;
    }

    @Override
    public Long hgetall(final KeyValueStreamingChannel<String, String> channel, final String key) {
        return null;
    }

    @Override
    public List<String> hkeys(final String key) {
        return null;
    }

    @Override
    public Long hkeys(final KeyStreamingChannel<String> channel, final String key) {
        return null;
    }

    @Override
    public Long hlen(final String key) {
        return null;
    }

    @Override
    public List<KeyValue<String, String>> hmget(final String key, final String... fields) {
        return null;
    }

    @Override
    public Long hmget(final KeyValueStreamingChannel<String, String> channel, final String key, final String... fields) {
        return null;
    }

    @Override
    public String hmset(final String key, final Map<String, String> map) {
        return null;
    }

    @Override
    public String hrandfield(final String key) {
        return null;
    }

    @Override
    public List<String> hrandfield(final String key, final long count) {
        return null;
    }

    @Override
    public KeyValue<String, String> hrandfieldWithvalues(final String key) {
        return null;
    }

    @Override
    public List<KeyValue<String, String>> hrandfieldWithvalues(final String key, final long count) {
        return null;
    }

    @Override
    public MapScanCursor<String, String> hscan(final String key) {
        return null;
    }

    @Override
    public MapScanCursor<String, String> hscan(final String key, final ScanArgs scanArgs) {
        return null;
    }

    @Override
    public MapScanCursor<String, String> hscan(final String key, final ScanCursor scanCursor, final ScanArgs scanArgs) {
        return null;
    }

    @Override
    public MapScanCursor<String, String> hscan(final String key, final ScanCursor scanCursor) {
        return null;
    }

    @Override
    public StreamScanCursor hscan(final KeyValueStreamingChannel<String, String> channel, final String key) {
        return null;
    }

    @Override
    public StreamScanCursor hscan(final KeyValueStreamingChannel<String, String> channel, final String key, final ScanArgs scanArgs) {
        return null;
    }

    @Override
    public StreamScanCursor hscan(final KeyValueStreamingChannel<String, String> channel, final String key, final ScanCursor scanCursor, final ScanArgs scanArgs) {
        return null;
    }

    @Override
    public StreamScanCursor hscan(final KeyValueStreamingChannel<String, String> channel, final String key, final ScanCursor scanCursor) {
        return null;
    }

    @Override
    public Boolean hset(final String key, final String field, final String value) {
        return null;
    }

    @Override
    public Long hset(final String s, final Map<String, String> map) {
        return null;
    }

    @Override
    public Boolean hsetnx(final String key, final String field, final String value) {
        return null;
    }

    @Override
    public Long hstrlen(final String key, final String field) {
        return null;
    }

    @Override
    public List<String> hvals(final String key) {
        return null;
    }

    @Override
    public Long hvals(final ValueStreamingChannel<String> channel, final String key) {
        return null;
    }

    @Override
    public String blmove(final String source, final String destination, final LMoveArgs args, final long timeout) {
        return null;
    }

    @Override
    public String blmove(final String source, final String destination, final LMoveArgs args, final double timeout) {
        return null;
    }

    @Override
    public KeyValue<String, List<String>> blmpop(final long l, final LMPopArgs lmPopArgs, final String... strings) {
        return null;
    }

    @Override
    public KeyValue<String, List<String>> blmpop(final double v, final LMPopArgs lmPopArgs, final String... strings) {
        return null;
    }

    @Override
    public KeyValue<String, String> blpop(final long timeout, final String... keys) {
        return null;
    }

    @Override
    public KeyValue<String, String> blpop(final double timeout, final String... keys) {
        return null;
    }

    @Override
    public KeyValue<String, String> brpop(final long timeout, final String... keys) {
        return null;
    }

    @Override
    public KeyValue<String, String> brpop(final double timeout, final String... keys) {
        return null;
    }

    @Override
    public String brpoplpush(final long timeout, final String source, final String destination) {
        return null;
    }

    @Override
    public String brpoplpush(final double timeout, final String source, final String destination) {
        return null;
    }

    @Override
    public String lindex(final String key, final long index) {
        return null;
    }

    @Override
    public Long linsert(final String key, final boolean before, final String pivot, final String value) {
        return null;
    }

    @Override
    public Long llen(final String key) {
        return null;
    }

    @Override
    public String lmove(final String source, final String destination, final LMoveArgs args) {
        return null;
    }

    @Override
    public KeyValue<String, List<String>> lmpop(final LMPopArgs lmPopArgs, final String... strings) {
        return null;
    }

    @Override
    public String lpop(final String key) {
        return null;
    }

    @Override
    public List<String> lpop(final String key, final long count) {
        return null;
    }

    @Override
    public Long lpos(final String s, final String s2) {
        return null;
    }

    @Override
    public Long lpos(final String s, final String s2, final LPosArgs lPosArgs) {
        return null;
    }

    @Override
    public List<Long> lpos(final String s, final String s2, final int i) {
        return null;
    }

    @Override
    public List<Long> lpos(final String s, final String s2, final int i, final LPosArgs lPosArgs) {
        return null;
    }

    @Override
    public Long lpush(final String key, final String... values) {
        return null;
    }

    @Override
    public Long lpushx(final String key, final String... values) {
        return null;
    }

    @Override
    public List<String> lrange(final String key, final long start, final long stop) {
        return null;
    }

    @Override
    public Long lrange(final ValueStreamingChannel<String> channel, final String key, final long start, final long stop) {
        return null;
    }

    @Override
    public Long lrem(final String key, final long count, final String value) {
        return null;
    }

    @Override
    public String lset(final String key, final long index, final String value) {
        return null;
    }

    @Override
    public String ltrim(final String key, final long start, final long stop) {
        return null;
    }

    @Override
    public String rpop(final String key) {
        return null;
    }

    @Override
    public List<String> rpop(final String key, final long count) {
        return null;
    }

    @Override
    public String rpoplpush(final String source, final String destination) {
        return null;
    }

    @Override
    public Long rpush(final String key, final String... values) {
        return null;
    }

    @Override
    public Long rpushx(final String key, final String... values) {
        return null;
    }

    @Override
    public <T> T eval(final String script, final ScriptOutputType type, final String... keys) {
        return null;
    }

    @Override
    public <T> T eval(final byte[] script, final ScriptOutputType type, final String... keys) {
        return null;
    }

    @Override
    public <T> T eval(final String script, final ScriptOutputType type, final String[] keys, final String... values) {
        return null;
    }

    @Override
    public <T> T eval(final byte[] script, final ScriptOutputType type, final String[] keys, final String... values) {
        return null;
    }

    @Override
    public <T> T evalReadOnly(final byte[] bytes, final ScriptOutputType scriptOutputType, final String[] strings, final String... strings2) {
        return null;
    }

    @Override
    public <T> T evalsha(final String digest, final ScriptOutputType type, final String... keys) {
        return null;
    }

    @Override
    public <T> T evalsha(final String digest, final ScriptOutputType type, final String[] keys, final String... values) {
        return null;
    }

    @Override
    public <T> T evalshaReadOnly(final String s, final ScriptOutputType scriptOutputType, final String[] strings, final String... strings2) {
        return null;
    }

    @Override
    public List<Boolean> scriptExists(final String... digests) {
        return null;
    }

    @Override
    public String scriptFlush() {
        return null;
    }

    @Override
    public String scriptFlush(final FlushMode flushMode) {
        return null;
    }

    @Override
    public String scriptKill() {
        return null;
    }

    @Override
    public String scriptLoad(final String script) {
        return null;
    }

    @Override
    public String scriptLoad(final byte[] script) {
        return null;
    }

    @Override
    public String digest(final String script) {
        return null;
    }

    @Override
    public String digest(final byte[] script) {
        return null;
    }

    @Override
    public String bgrewriteaof() {
        return null;
    }

    @Override
    public String bgsave() {
        return null;
    }

    @Override
    public String clientCaching(final boolean enabled) {
        return null;
    }

    @Override
    public String clientGetname() {
        return null;
    }

    @Override
    public Long clientGetredir() {
        return null;
    }

    @Override
    public String clientSetname(final String name) {
        return null;
    }

    @Override
    public String clientTracking(final TrackingArgs args) {
        return null;
    }

    @Override
    public String clientKill(final String addr) {
        return null;
    }

    @Override
    public Long clientKill(final KillArgs killArgs) {
        return null;
    }

    @Override
    public Long clientUnblock(final long id, final UnblockType type) {
        return null;
    }

    @Override
    public String clientPause(final long timeout) {
        return null;
    }

    @Override
    public String clientList() {
        return null;
    }

    @Override
    public String clientNoEvict(final boolean b) {
        return null;
    }

    @Override
    public Long clientId() {
        return null;
    }

    @Override
    public List<Object> command() {
        return null;
    }

    @Override
    public List<Object> commandInfo(final String... commands) {
        return null;
    }

    @Override
    public List<Object> commandInfo(final CommandType... commands) {
        return null;
    }

    @Override
    public Long commandCount() {
        return null;
    }

    @Override
    public Map<String, String> configGet(final String parameter) {
        return null;
    }

    @Override
    public Map<String, String> configGet(final String... strings) {
        return null;
    }

    @Override
    public String configResetstat() {
        return null;
    }

    @Override
    public String configRewrite() {
        return null;
    }

    @Override
    public String configSet(final String parameter, final String value) {
        return null;
    }

    @Override
    public String configSet(final Map<String, String> map) {
        return null;
    }

    @Override
    public Long dbsize() {
        return null;
    }

    @Override
    public String debugCrashAndRecover(final Long delay) {
        return null;
    }

    @Override
    public String debugHtstats(final int db) {
        return null;
    }

    @Override
    public String debugObject(final String key) {
        return null;
    }

    @Override
    public void debugOom() {

    }

    @Override
    public void debugSegfault() {

    }

    @Override
    public String debugReload() {
        return null;
    }

    @Override
    public String debugRestart(final Long delay) {
        return null;
    }

    @Override
    public String debugSdslen(final String key) {
        return null;
    }

    @Override
    public String flushall() {
        return null;
    }

    @Override
    public String flushall(final FlushMode flushMode) {
        return null;
    }

    @Override
    public String flushallAsync() {
        return null;
    }

    @Override
    public String flushdb() {
        return null;
    }

    @Override
    public String flushdb(final FlushMode flushMode) {
        return null;
    }

    @Override
    public String flushdbAsync() {
        return null;
    }

    @Override
    public String info() {
        return null;
    }

    @Override
    public String info(final String section) {
        return null;
    }

    @Override
    public Date lastsave() {
        return null;
    }

    @Override
    public Long memoryUsage(final String key) {
        return null;
    }

    @Override
    public String replicaof(final String host, final int port) {
        return null;
    }

    @Override
    public String replicaofNoOne() {
        return null;
    }

    @Override
    public String save() {
        return null;
    }

    @Override
    public void shutdown(final boolean save) {

    }

    @Override
    public void shutdown(final ShutdownArgs shutdownArgs) {

    }

    @Override
    public String slaveof(final String host, final int port) {
        return null;
    }

    @Override
    public String slaveofNoOne() {
        return null;
    }

    @Override
    public List<Object> slowlogGet() {
        return null;
    }

    @Override
    public List<Object> slowlogGet(final int count) {
        return null;
    }

    @Override
    public Long slowlogLen() {
        return null;
    }

    @Override
    public String slowlogReset() {
        return null;
    }

    @Override
    public List<String> time() {
        return null;
    }

    @Override
    public Long sadd(final String key, final String... members) {
        return null;
    }

    @Override
    public Long scard(final String key) {
        return null;
    }

    @Override
    public Set<String> sdiff(final String... keys) {
        return null;
    }

    @Override
    public Long sdiff(final ValueStreamingChannel<String> channel, final String... keys) {
        return null;
    }

    @Override
    public Long sdiffstore(final String destination, final String... keys) {
        return null;
    }

    @Override
    public Set<String> sinter(final String... keys) {
        return null;
    }

    @Override
    public Long sinter(final ValueStreamingChannel<String> channel, final String... keys) {
        return null;
    }

    @Override
    public Long sintercard(final String... strings) {
        return null;
    }

    @Override
    public Long sintercard(final long l, final String... strings) {
        return null;
    }

    @Override
    public Long sinterstore(final String destination, final String... keys) {
        return null;
    }

    @Override
    public Boolean sismember(final String key, final String member) {
        return null;
    }

    @Override
    public Boolean smove(final String source, final String destination, final String member) {
        return null;
    }

    @Override
    public Set<String> smembers(final String key) {
        return null;
    }

    @Override
    public Long smembers(final ValueStreamingChannel<String> channel, final String key) {
        return null;
    }

    @Override
    public List<Boolean> smismember(final String key, final String... members) {
        return null;
    }

    @Override
    public String spop(final String key) {
        return null;
    }

    @Override
    public Set<String> spop(final String key, final long count) {
        return null;
    }

    @Override
    public String srandmember(final String key) {
        return null;
    }

    @Override
    public List<String> srandmember(final String key, final long count) {
        return null;
    }

    @Override
    public Long srandmember(final ValueStreamingChannel<String> channel, final String key, final long count) {
        return null;
    }

    @Override
    public Long srem(final String key, final String... members) {
        return null;
    }

    @Override
    public Set<String> sunion(final String... keys) {
        return null;
    }

    @Override
    public Long sunion(final ValueStreamingChannel<String> channel, final String... keys) {
        return null;
    }

    @Override
    public Long sunionstore(final String destination, final String... keys) {
        return null;
    }

    @Override
    public ValueScanCursor<String> sscan(final String key) {
        return null;
    }

    @Override
    public ValueScanCursor<String> sscan(final String key, final ScanArgs scanArgs) {
        return null;
    }

    @Override
    public ValueScanCursor<String> sscan(final String key, final ScanCursor scanCursor, final ScanArgs scanArgs) {
        return null;
    }

    @Override
    public ValueScanCursor<String> sscan(final String key, final ScanCursor scanCursor) {
        return null;
    }

    @Override
    public StreamScanCursor sscan(final ValueStreamingChannel<String> channel, final String key) {
        return null;
    }

    @Override
    public StreamScanCursor sscan(final ValueStreamingChannel<String> channel, final String key, final ScanArgs scanArgs) {
        return null;
    }

    @Override
    public StreamScanCursor sscan(final ValueStreamingChannel<String> channel, final String key, final ScanCursor scanCursor, final ScanArgs scanArgs) {
        return null;
    }

    @Override
    public StreamScanCursor sscan(final ValueStreamingChannel<String> channel, final String key, final ScanCursor scanCursor) {
        return null;
    }

    @Override
    public KeyValue<String, ScoredValue<String>> bzpopmin(final long timeout, final String... keys) {
        return null;
    }

    @Override
    public KeyValue<String, ScoredValue<String>> bzpopmin(final double timeout, final String... keys) {
        return null;
    }

    @Override
    public KeyValue<String, ScoredValue<String>> bzpopmax(final long timeout, final String... keys) {
        return null;
    }

    @Override
    public KeyValue<String, ScoredValue<String>> bzpopmax(final double timeout, final String... keys) {
        return null;
    }

    @Override
    public Long zadd(final String key, final double score, final String member) {
        return null;
    }

    @Override
    public Long zadd(final String key, final Object... scoresAndValues) {
        return null;
    }

    @Override
    public Long zadd(final String key, final ScoredValue<String>... scoredValues) {
        return null;
    }

    @Override
    public Long zadd(final String key, final ZAddArgs zAddArgs, final double score, final String member) {
        return null;
    }

    @Override
    public Long zadd(final String key, final ZAddArgs zAddArgs, final Object... scoresAndValues) {
        return null;
    }

    @Override
    public Long zadd(final String key, final ZAddArgs zAddArgs, final ScoredValue<String>... scoredValues) {
        return null;
    }

    @Override
    public Double zaddincr(final String key, final double score, final String member) {
        return null;
    }

    @Override
    public Double zaddincr(final String key, final ZAddArgs zAddArgs, final double score, final String member) {
        return null;
    }

    @Override
    public Long zcard(final String key) {
        return null;
    }

    @Override
    public Long zcount(final String key, final double min, final double max) {
        return null;
    }

    @Override
    public Long zcount(final String key, final String min, final String max) {
        return null;
    }

    @Override
    public Long zcount(final String key, final Range<? extends Number> range) {
        return null;
    }

    @Override
    public List<String> zdiff(final String... keys) {
        return null;
    }

    @Override
    public Long zdiffstore(final String destKey, final String... srcKeys) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zdiffWithScores(final String... keys) {
        return null;
    }

    @Override
    public Double zincrby(final String key, final double amount, final String member) {
        return null;
    }

    @Override
    public List<String> zinter(final String... keys) {
        return null;
    }

    @Override
    public List<String> zinter(final ZAggregateArgs aggregateArgs, final String... keys) {
        return null;
    }

    @Override
    public Long zintercard(final String... strings) {
        return null;
    }

    @Override
    public Long zintercard(final long l, final String... strings) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zinterWithScores(final ZAggregateArgs aggregateArgs, final String... keys) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zinterWithScores(final String... keys) {
        return null;
    }

    @Override
    public Long zinterstore(final String destination, final String... keys) {
        return null;
    }

    @Override
    public Long zinterstore(final String destination, final ZStoreArgs storeArgs, final String... keys) {
        return null;
    }

    @Override
    public Long zlexcount(final String key, final String min, final String max) {
        return null;
    }

    @Override
    public Long zlexcount(final String key, final Range<? extends String> range) {
        return null;
    }

    @Override
    public List<Double> zmscore(final String key, final String... members) {
        return null;
    }

    @Override
    public ScoredValue<String> zpopmin(final String key) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zpopmin(final String key, final long count) {
        return null;
    }

    @Override
    public ScoredValue<String> zpopmax(final String key) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zpopmax(final String key, final long count) {
        return null;
    }

    @Override
    public String zrandmember(final String key) {
        return null;
    }

    @Override
    public List<String> zrandmember(final String key, final long count) {
        return null;
    }

    @Override
    public ScoredValue<String> zrandmemberWithScores(final String key) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zrandmemberWithScores(final String key, final long count) {
        return null;
    }

    @Override
    public List<String> zrange(final String key, final long start, final long stop) {
        return null;
    }

    @Override
    public Long zrange(final ValueStreamingChannel<String> channel, final String key, final long start, final long stop) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zrangeWithScores(final String key, final long start, final long stop) {
        return null;
    }

    @Override
    public Long zrangeWithScores(final ScoredValueStreamingChannel<String> channel, final String key, final long start, final long stop) {
        return null;
    }

    @Override
    public List<String> zrangebylex(final String key, final String min, final String max) {
        return null;
    }

    @Override
    public List<String> zrangebylex(final String key, final Range<? extends String> range) {
        return null;
    }

    @Override
    public List<String> zrangebylex(final String key, final String min, final String max, final long offset, final long count) {
        return null;
    }

    @Override
    public List<String> zrangebylex(final String key, final Range<? extends String> range, final Limit limit) {
        return null;
    }

    @Override
    public List<String> zrangebyscore(final String key, final double min, final double max) {
        return null;
    }

    @Override
    public List<String> zrangebyscore(final String key, final String min, final String max) {
        return null;
    }

    @Override
    public List<String> zrangebyscore(final String key, final Range<? extends Number> range) {
        return null;
    }

    @Override
    public List<String> zrangebyscore(final String key, final double min, final double max, final long offset, final long count) {
        return null;
    }

    @Override
    public List<String> zrangebyscore(final String key, final String min, final String max, final long offset, final long count) {
        return null;
    }

    @Override
    public List<String> zrangebyscore(final String key, final Range<? extends Number> range, final Limit limit) {
        return null;
    }

    @Override
    public Long zrangebyscore(final ValueStreamingChannel<String> channel, final String key, final double min, final double max) {
        return null;
    }

    @Override
    public Long zrangebyscore(final ValueStreamingChannel<String> channel, final String key, final String min, final String max) {
        return null;
    }

    @Override
    public Long zrangebyscore(final ValueStreamingChannel<String> channel, final String key, final Range<? extends Number> range) {
        return null;
    }

    @Override
    public Long zrangebyscore(final ValueStreamingChannel<String> channel, final String key, final double min, final double max, final long offset, final long count) {
        return null;
    }

    @Override
    public Long zrangebyscore(final ValueStreamingChannel<String> channel, final String key, final String min, final String max, final long offset, final long count) {
        return null;
    }

    @Override
    public Long zrangebyscore(final ValueStreamingChannel<String> channel, final String key, final Range<? extends Number> range, final Limit limit) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zrangebyscoreWithScores(final String key, final double min, final double max) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zrangebyscoreWithScores(final String key, final String min, final String max) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zrangebyscoreWithScores(final String key, final Range<? extends Number> range) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zrangebyscoreWithScores(final String key, final double min, final double max, final long offset, final long count) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zrangebyscoreWithScores(final String key, final String min, final String max, final long offset, final long count) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zrangebyscoreWithScores(final String key, final Range<? extends Number> range, final Limit limit) {
        return null;
    }

    @Override
    public Long zrangebyscoreWithScores(final ScoredValueStreamingChannel<String> channel, final String key, final double min, final double max) {
        return null;
    }

    @Override
    public Long zrangebyscoreWithScores(final ScoredValueStreamingChannel<String> channel, final String key, final String min, final String max) {
        return null;
    }

    @Override
    public Long zrangebyscoreWithScores(final ScoredValueStreamingChannel<String> channel, final String key, final Range<? extends Number> range) {
        return null;
    }

    @Override
    public Long zrangebyscoreWithScores(final ScoredValueStreamingChannel<String> channel, final String key, final double min, final double max, final long offset, final long count) {
        return null;
    }

    @Override
    public Long zrangebyscoreWithScores(final ScoredValueStreamingChannel<String> channel, final String key, final String min, final String max, final long offset, final long count) {
        return null;
    }

    @Override
    public Long zrangebyscoreWithScores(final ScoredValueStreamingChannel<String> channel, final String key, final Range<? extends Number> range, final Limit limit) {
        return null;
    }

    @Override
    public Long zrangestore(final String s, final String k1, final Range<Long> range) {
        return null;
    }

    @Override
    public Long zrangestorebylex(final String dstKey, final String srcKey, final Range<? extends String> range, final Limit limit) {
        return null;
    }

    @Override
    public Long zrangestorebyscore(final String dstKey, final String srcKey, final Range<? extends Number> range, final Limit limit) {
        return null;
    }

    @Override
    public Long zrank(final String key, final String member) {
        return null;
    }

    @Override
    public Long zrem(final String key, final String... members) {
        return null;
    }

    @Override
    public Long zremrangebylex(final String key, final String min, final String max) {
        return null;
    }

    @Override
    public Long zremrangebylex(final String key, final Range<? extends String> range) {
        return null;
    }

    @Override
    public Long zremrangebyrank(final String key, final long start, final long stop) {
        return null;
    }

    @Override
    public Long zremrangebyscore(final String key, final double min, final double max) {
        return null;
    }

    @Override
    public Long zremrangebyscore(final String key, final String min, final String max) {
        return null;
    }

    @Override
    public Long zremrangebyscore(final String key, final Range<? extends Number> range) {
        return null;
    }

    @Override
    public List<String> zrevrange(final String key, final long start, final long stop) {
        return null;
    }

    @Override
    public Long zrevrange(final ValueStreamingChannel<String> channel, final String key, final long start, final long stop) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zrevrangeWithScores(final String key, final long start, final long stop) {
        return null;
    }

    @Override
    public Long zrevrangeWithScores(final ScoredValueStreamingChannel<String> channel, final String key, final long start, final long stop) {
        return null;
    }

    @Override
    public List<String> zrevrangebylex(final String key, final Range<? extends String> range) {
        return null;
    }

    @Override
    public List<String> zrevrangebylex(final String key, final Range<? extends String> range, final Limit limit) {
        return null;
    }

    @Override
    public List<String> zrevrangebyscore(final String key, final double max, final double min) {
        return null;
    }

    @Override
    public List<String> zrevrangebyscore(final String key, final String max, final String min) {
        return null;
    }

    @Override
    public List<String> zrevrangebyscore(final String key, final Range<? extends Number> range) {
        return null;
    }

    @Override
    public List<String> zrevrangebyscore(final String key, final double max, final double min, final long offset, final long count) {
        return null;
    }

    @Override
    public List<String> zrevrangebyscore(final String key, final String max, final String min, final long offset, final long count) {
        return null;
    }

    @Override
    public List<String> zrevrangebyscore(final String key, final Range<? extends Number> range, final Limit limit) {
        return null;
    }

    @Override
    public Long zrevrangebyscore(final ValueStreamingChannel<String> channel, final String key, final double max, final double min) {
        return null;
    }

    @Override
    public Long zrevrangebyscore(final ValueStreamingChannel<String> channel, final String key, final String max, final String min) {
        return null;
    }

    @Override
    public Long zrevrangebyscore(final ValueStreamingChannel<String> channel, final String key, final Range<? extends Number> range) {
        return null;
    }

    @Override
    public Long zrevrangebyscore(final ValueStreamingChannel<String> channel, final String key, final double max, final double min, final long offset, final long count) {
        return null;
    }

    @Override
    public Long zrevrangebyscore(final ValueStreamingChannel<String> channel, final String key, final String max, final String min, final long offset, final long count) {
        return null;
    }

    @Override
    public Long zrevrangebyscore(final ValueStreamingChannel<String> channel, final String key, final Range<? extends Number> range, final Limit limit) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zrevrangebyscoreWithScores(final String key, final double max, final double min) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zrevrangebyscoreWithScores(final String key, final String max, final String min) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zrevrangebyscoreWithScores(final String key, final Range<? extends Number> range) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zrevrangebyscoreWithScores(final String key, final double max, final double min, final long offset, final long count) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zrevrangebyscoreWithScores(final String key, final String max, final String min, final long offset, final long count) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zrevrangebyscoreWithScores(final String key, final Range<? extends Number> range, final Limit limit) {
        return null;
    }

    @Override
    public Long zrevrangebyscoreWithScores(final ScoredValueStreamingChannel<String> channel, final String key, final double max, final double min) {
        return null;
    }

    @Override
    public Long zrevrangebyscoreWithScores(final ScoredValueStreamingChannel<String> channel, final String key, final String max, final String min) {
        return null;
    }

    @Override
    public Long zrevrangebyscoreWithScores(final ScoredValueStreamingChannel<String> channel, final String key, final Range<? extends Number> range) {
        return null;
    }

    @Override
    public Long zrevrangebyscoreWithScores(final ScoredValueStreamingChannel<String> channel, final String key, final double max, final double min, final long offset, final long count) {
        return null;
    }

    @Override
    public Long zrevrangebyscoreWithScores(final ScoredValueStreamingChannel<String> channel, final String key, final String max, final String min, final long offset, final long count) {
        return null;
    }

    @Override
    public Long zrevrangebyscoreWithScores(final ScoredValueStreamingChannel<String> channel, final String key, final Range<? extends Number> range, final Limit limit) {
        return null;
    }

    @Override
    public Long zrevrangestore(final String s, final String k1, final Range<Long> range) {
        return null;
    }

    @Override
    public Long zrevrangestorebylex(final String dstKey, final String srcKey, final Range<? extends String> range, final Limit limit) {
        return null;
    }

    @Override
    public Long zrevrangestorebyscore(final String dstKey, final String srcKey, final Range<? extends Number> range, final Limit limit) {
        return null;
    }

    @Override
    public Long zrevrank(final String key, final String member) {
        return null;
    }

    @Override
    public ScoredValueScanCursor<String> zscan(final String key) {
        return null;
    }

    @Override
    public ScoredValueScanCursor<String> zscan(final String key, final ScanArgs scanArgs) {
        return null;
    }

    @Override
    public ScoredValueScanCursor<String> zscan(final String key, final ScanCursor scanCursor, final ScanArgs scanArgs) {
        return null;
    }

    @Override
    public ScoredValueScanCursor<String> zscan(final String key, final ScanCursor scanCursor) {
        return null;
    }

    @Override
    public StreamScanCursor zscan(final ScoredValueStreamingChannel<String> channel, final String key) {
        return null;
    }

    @Override
    public StreamScanCursor zscan(final ScoredValueStreamingChannel<String> channel, final String key, final ScanArgs scanArgs) {
        return null;
    }

    @Override
    public StreamScanCursor zscan(final ScoredValueStreamingChannel<String> channel, final String key, final ScanCursor scanCursor, final ScanArgs scanArgs) {
        return null;
    }

    @Override
    public StreamScanCursor zscan(final ScoredValueStreamingChannel<String> channel, final String key, final ScanCursor scanCursor) {
        return null;
    }

    @Override
    public Double zscore(final String key, final String member) {
        return null;
    }

    @Override
    public List<String> zunion(final String... keys) {
        return null;
    }

    @Override
    public List<String> zunion(final ZAggregateArgs aggregateArgs, final String... keys) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zunionWithScores(final ZAggregateArgs aggregateArgs, final String... keys) {
        return null;
    }

    @Override
    public List<ScoredValue<String>> zunionWithScores(final String... keys) {
        return null;
    }

    @Override
    public Long zunionstore(final String destination, final String... keys) {
        return null;
    }

    @Override
    public Long zunionstore(final String destination, final ZStoreArgs storeArgs, final String... keys) {
        return null;
    }

    @Override
    public Long xack(final String key, final String group, final String... messageIds) {
        return null;
    }

    @Override
    public String xadd(final String key, final Map<String, String> body) {
        return null;
    }

    @Override
    public String xadd(final String key, final XAddArgs args, final Map<String, String> body) {
        return null;
    }

    @Override
    public String xadd(final String key, final Object... keysAndValues) {
        return null;
    }

    @Override
    public String xadd(final String key, final XAddArgs args, final Object... keysAndValues) {
        return null;
    }

    @Override
    public ClaimedMessages<String, String> xautoclaim(final String key, final XAutoClaimArgs<String> args) {
        return null;
    }

    @Override
    public List<StreamMessage<String, String>> xclaim(final String key, final Consumer<String> consumer, final long minIdleTime, final String... messageIds) {
        return null;
    }

    @Override
    public List<StreamMessage<String, String>> xclaim(final String key, final Consumer<String> consumer, final XClaimArgs args, final String... messageIds) {
        return null;
    }

    @Override
    public Long xdel(final String key, final String... messageIds) {
        return null;
    }

    @Override
    public String xgroupCreate(final XReadArgs.StreamOffset<String> streamOffset, final String group) {
        return null;
    }

    @Override
    public String xgroupCreate(final XReadArgs.StreamOffset<String> streamOffset, final String group, final XGroupCreateArgs args) {
        return null;
    }

    @Override
    public Boolean xgroupCreateconsumer(final String key, final Consumer<String> consumer) {
        return null;
    }

    @Override
    public Long xgroupDelconsumer(final String key, final Consumer<String> consumer) {
        return null;
    }

    @Override
    public Boolean xgroupDestroy(final String key, final String group) {
        return null;
    }

    @Override
    public String xgroupSetid(final XReadArgs.StreamOffset<String> streamOffset, final String group) {
        return null;
    }

    @Override
    public List<Object> xinfoStream(final String key) {
        return null;
    }

    @Override
    public List<Object> xinfoGroups(final String key) {
        return null;
    }

    @Override
    public List<Object> xinfoConsumers(final String key, final String group) {
        return null;
    }

    @Override
    public Long xlen(final String key) {
        return null;
    }

    @Override
    public PendingMessages xpending(final String key, final String group) {
        return null;
    }

    @Override
    public List<PendingMessage> xpending(final String key, final String group, final Range<String> range, final Limit limit) {
        return null;
    }

    @Override
    public List<PendingMessage> xpending(final String key, final Consumer<String> consumer, final Range<String> range, final Limit limit) {
        return null;
    }

    @Override
    public List<PendingMessage> xpending(final String key, final XPendingArgs<String> args) {
        return null;
    }

    @Override
    public List<StreamMessage<String, String>> xrange(final String key, final Range<String> range) {
        return null;
    }

    @Override
    public List<StreamMessage<String, String>> xrange(final String key, final Range<String> range, final Limit limit) {
        return null;
    }

    @Override
    public List<StreamMessage<String, String>> xread(final XReadArgs.StreamOffset<String>... streams) {
        return null;
    }

    @Override
    public List<StreamMessage<String, String>> xread(final XReadArgs args, final XReadArgs.StreamOffset<String>... streams) {
        return null;
    }

    @Override
    public List<StreamMessage<String, String>> xreadgroup(final Consumer<String> consumer, final XReadArgs.StreamOffset<String>... streams) {
        return null;
    }

    @Override
    public List<StreamMessage<String, String>> xreadgroup(final Consumer<String> consumer, final XReadArgs args, final XReadArgs.StreamOffset<String>... streams) {
        return null;
    }

    @Override
    public List<StreamMessage<String, String>> xrevrange(final String key, final Range<String> range) {
        return null;
    }

    @Override
    public List<StreamMessage<String, String>> xrevrange(final String key, final Range<String> range, final Limit limit) {
        return null;
    }

    @Override
    public Long xtrim(final String key, final long count) {
        return null;
    }

    @Override
    public Long xtrim(final String key, final boolean approximateTrimming, final long count) {
        return null;
    }

    @Override
    public Long xtrim(final String key, final XTrimArgs args) {
        return null;
    }

    @Override
    public String discard() {
        return null;
    }

    @Override
    public TransactionResult exec() {
        return null;
    }

    @Override
    public String multi() {
        return null;
    }

    @Override
    public String watch(final String... keys) {
        return null;
    }

    @Override
    public String unwatch() {
        return null;
    }

}
