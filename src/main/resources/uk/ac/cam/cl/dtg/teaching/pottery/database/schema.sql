--
-- pottery-backend - Backend API for testing programming exercises
-- Copyright © 2015-2018 BlueOptima Limited, Andrew Rice (acr31@cam.ac.uk)
--
-- This program is free software: you can redistribute it and/or modify
-- it under the terms of the GNU Affero General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- This program is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU Affero General Public License for more details.
--
-- You should have received a copy of the GNU Affero General Public License
-- along with this program.  If not, see <http://www.gnu.org/licenses/>.
--

CREATE TABLE outputs (
    repoid character varying(255) NOT NULL,
    tag character varying(255) NOT NULL,
    action character varying(255) NOT NULL,
    position integer NOT NULL,
    step character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    timems bigint DEFAULT '-1'::integer NOT NULL,
    output text,
    containerName character varying(255),
    primary key (repoid,tag,action,position)
);

CREATE TABLE repos (
    repoid character varying(255) NOT NULL,
    taskid character varying(255) NOT NULL,
    using_testing_version boolean DEFAULT true NOT NULL,
    expirydate timestamp with time zone,
    remote character varying(255) DEFAULT '' NOT NULL,
    variant character varying(255) NOT NULL,
    errormessage text,
    mutationid integer NOT NULL,
    problemstatement text,
    primary key (repoid)
);

CREATE TABLE submissions (
    repoid character varying(255) NOT NULL,
    tag character varying(255) NOT NULL,
    action character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    errormessage text,
    datescheduled timestamp without time zone,
    primary key (repoid,tag,action)
);

CREATE TABLE tasks (
    taskid character varying(255) NOT NULL,
    registeredtag character varying(255),
    retired boolean DEFAULT false NOT NULL,
    testingcopyid character varying(255),
    registeredcopyid character varying(255),
    remote character varying(255) default '' not null,
    primary key (taskid)
);

CREATE TABLE config (
    key character varying(255) NOT NULL,
    value text NOT NULL,
    primary key (key)
);