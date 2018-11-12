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

--
-- PostgreSQL database dump
--

-- Dumped from database version 9.5.3
-- Dumped by pg_dump version 9.5.3

SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: 
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


SET search_path = public, pg_catalog;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: outputs; Type: TABLE; Schema: public; Owner: pottery
--

CREATE TABLE outputs (
    repoid character varying(255) NOT NULL,
    tag character varying(255) NOT NULL,
    action character varying(255) NOT NULL,
    position integer NOT NULL,
    step character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    timems bigint DEFAULT '-1'::integer NOT NULL,
    output text
);


ALTER TABLE outputs OWNER TO pottery;

--
-- Name: repos; Type: TABLE; Schema: public; Owner: pottery
--

CREATE TABLE repos (
    repoid character varying(255) NOT NULL,
    taskid character varying(255) NOT NULL,
    using_testing_version boolean DEFAULT true NOT NULL,
    expirydate timestamp with time zone,
    remote character varying(255) DEFAULT '' NOT NULL,
    variant character varying(255) NOT NULL,
    errormessage text
);


ALTER TABLE repos OWNER TO pottery;

--
-- Name: submissions; Type: TABLE; Schema: public; Owner: pottery
--

CREATE TABLE submissions (
    repoid character varying(255) NOT NULL,
    tag character varying(255) NOT NULL,
    action character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    errormessage text,
    datescheduled timestamp without time zone
);


ALTER TABLE submissions OWNER TO pottery;

--
-- Name: tasks; Type: TABLE; Schema: public; Owner: pottery
--

CREATE TABLE tasks (
    taskid character varying(255) NOT NULL,
    registeredtag character varying(255),
    retired boolean DEFAULT false NOT NULL,
    testingcopyid character varying(255),
    registeredcopyid character varying(255),
    remote character varying(255) default '' not null
);


ALTER TABLE tasks OWNER TO pottery;

--
-- Name: outputs_pkey; Type: CONSTRAINT; Schema: public; Owner: pottery
--

ALTER TABLE ONLY outputs
    ADD CONSTRAINT outputs_pkey PRIMARY KEY (repoid, tag, action, position);


--
-- Name: repos_pkey; Type: CONSTRAINT; Schema: public; Owner: pottery
--

ALTER TABLE ONLY repos
    ADD CONSTRAINT repos_pkey PRIMARY KEY (repoid);


--
-- Name: submissions_pkey; Type: CONSTRAINT; Schema: public; Owner: pottery
--

ALTER TABLE ONLY submissions
    ADD CONSTRAINT submissions_pkey PRIMARY KEY (repoid, tag, action);


--
-- Name: tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: pottery
--

ALTER TABLE ONLY tasks
    ADD CONSTRAINT tasks_pkey PRIMARY KEY (taskid);


--
-- Name: public; Type: ACL; Schema: -; Owner: postgres
--

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM postgres;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO PUBLIC;


--
-- Name: repos; Type: ACL; Schema: public; Owner: pottery
--

REVOKE ALL ON TABLE repos FROM PUBLIC;
REVOKE ALL ON TABLE repos FROM pottery;
GRANT ALL ON TABLE repos TO pottery;
GRANT ALL ON TABLE repos TO postgres;


--
-- PostgreSQL database dump complete
--

