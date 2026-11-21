// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/// @title Bulwark Registry
/// @notice Append-only public list of flagged domains and APKs.
///         Entries can only be added by the backend wallet (owner); nothing can be deleted.
///         Lookups use an 8-byte prefix of the SHA-256 hash so clients never send the full URL.
/// @dev Hash scheme is fixed by the PRD: SHA-256 (not keccak256), normalized domain
///      (lowercase, no trailing dot), first 8 bytes used as the lookup key.
contract Registry {
    uint8 public constant KIND_DOMAIN = 1;
    uint8 public constant KIND_APK = 2;

    struct Entry {
        bytes32 hash; // full SHA-256 of the normalized domain / APK bytes
        uint8 kind; // 1 = domain, 2 = apk
        uint256 reportedAt; // block timestamp when the entry was added
        address reporter; // backend wallet that submitted the report
    }

    address public owner;

    /// @notice Full entries keyed by their 32-byte SHA-256 hash.
    mapping(bytes32 => Entry) public entries;

    /// @notice Fast lookup keyed by the 8-byte hash prefix sent by clients.
    mapping(bytes8 => bool) public flaggedPrefix;

    /// @notice Number of entries ever appended. Never decreases (append-only).
    uint256 public totalReports;

    event Reported(bytes32 indexed hash, uint8 kind, uint256 at);

    error NotOwner();
    error InvalidKind(uint8 kind);
    error AlreadyReported(bytes32 hash);

    modifier onlyOwner() {
        if (msg.sender != owner) revert NotOwner();
        _;
    }

    constructor() {
        owner = msg.sender;
    }

    /// @notice Append a new flagged entry. Only the backend wallet may call this.
    /// @param hash Full SHA-256 hash of the normalized domain (UTF-8 bytes) or APK contents.
    /// @param kind KIND_DOMAIN (1) or KIND_APK (2).
    function report(bytes32 hash, uint8 kind) external onlyOwner {
        if (kind != KIND_DOMAIN && kind != KIND_APK) revert InvalidKind(kind);
        if (entries[hash].reportedAt != 0) revert AlreadyReported(hash);

        entries[hash] = Entry({
            hash: hash,
            kind: kind,
            reportedAt: block.timestamp,
            reporter: msg.sender
        });
        flaggedPrefix[bytes8(hash)] = true;
        totalReports += 1;

        emit Reported(hash, kind, block.timestamp);
    }

    /// @notice True if the full hash (via its 8-byte prefix) has been flagged.
    function isFlagged(bytes32 hash) external view returns (bool) {
        return flaggedPrefix[bytes8(hash)];
    }

    /// @notice True if an 8-byte prefix has been flagged. This is what the mediation API uses.
    function isFlaggedPrefix(bytes8 prefix) external view returns (bool) {
        return flaggedPrefix[prefix];
    }

    /// @notice Return the full entry for a hash (zeroed struct if not present).
    function getEntry(bytes32 hash) external view returns (Entry memory) {
        return entries[hash];
    }
}
