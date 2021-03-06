--*************************GO-LICENSE-START*********************************
-- Copyright 2014 ThoughtWorks, Inc.
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--    http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--*************************GO-LICENSE-END***********************************

ALTER TABLE materials DROP COLUMN serveralias;

SET @delimiter = '<|>';

UPDATE materials SET
    fingerprint = HASH('SHA256', STRINGTOUTF8(concat('type=', type, @delimiter, 'pipelineName=', pipelineName, @delimiter, 'stageName=', stageName)), 1)
    WHERE type = 'DependencyMaterial';

--//@UNDO

ALTER TABLE materials ADD COLUMN serveralias VARCHAR(255);

SET @delimiter = '<|>';

UPDATE materials SET
    fingerprint = HASH('SHA256', STRINGTOUTF8(concat('type=', type, @delimiter, 'pipelineName=', pipelineName, @delimiter, 'stageName=', stageName, @delimiter, 'serverAlias=null')), 1)
    WHERE type = 'DependencyMaterial';

