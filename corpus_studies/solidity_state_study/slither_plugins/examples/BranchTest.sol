// SPDX-License-Identifier: BSD-3
pragma solidity ^0.8.4;

contract Foo {
    uint x;
    
    constructor() {
        x = 0;
    }

    //Currently, this function will be flagged as evidence for state, since 
    //x is used in an if statement, but neither branch leads to a throw/revert.
    //I think I have a fix for this, but it's not included in this current patch.
    function bar() public view returns (uint) {
        if (x == 0) {
            return 0; 
        }
        else {
            return 1;
        }
    }
}
